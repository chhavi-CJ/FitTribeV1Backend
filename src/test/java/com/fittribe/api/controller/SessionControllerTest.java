package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.StartSessionRequest;
import com.fittribe.api.dto.response.StartSessionResponse;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.SavedRoutineRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SessionController#startSession}, focused on the
 * client-supplied UUID idempotency path introduced in
 * {@code feature/b1-05-track-b-stage-1-client-uuid}.
 *
 * <p>Mirrors the pattern in {@code AdminJobTriggerControllerTest}: pure
 * Mockito on JPA-repository interfaces; concrete-class dependencies that
 * the {@code /start} handler doesn't touch are passed as {@code null}
 * (fields constructed but never dereferenced for this endpoint, including
 * {@link org.springframework.transaction.support.TransactionTemplate}
 * which is only invoked from the finish flow).
 */
class SessionControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENT_ID = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private WorkoutSessionRepository sessionRepo;
    private SavedRoutineRepository   routineRepo;
    private SessionController        controller;
    private Authentication           auth;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(WorkoutSessionRepository.class);
        routineRepo = mock(SavedRoutineRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();

        auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(USER_ID);

        // Default: saveAndFlush echoes the entity but populates startedAt
        // (real Hibernate fires @PrePersist on insert; mocked path skips it).
        when(sessionRepo.saveAndFlush(any(WorkoutSession.class))).thenAnswer(inv -> {
            WorkoutSession s = inv.getArgument(0);
            if (s.getStartedAt() == null) s.setStartedAt(Instant.now());
            return s;
        });

        // Default: nothing in cooldown, no IN_PROGRESS, no clientId match
        when(sessionRepo.findFirstByUserIdAndStatusAndFinishedAtAfter(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(sessionRepo.findFirstByUserIdAndStatusOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(sessionRepo.findById(any())).thenReturn(Optional.empty());

        controller = new SessionController(
                sessionRepo, null, null, null, null, null, null, null,
                objectMapper, null, null, routineRepo, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static StartSessionRequest reqWithClientId(UUID clientId) {
        return new StartSessionRequest("Test session", null, "AI_PLAN", null, null, clientId);
    }

    private static WorkoutSession session(UUID id, UUID userId, String status) {
        WorkoutSession s = new WorkoutSession();
        s.setId(id);
        s.setUserId(userId);
        s.setStatus(status);
        s.setStartedAt(Instant.parse("2026-05-09T10:00:00Z"));
        return s;
    }

    @SuppressWarnings("unchecked")
    private static StartSessionResponse body(ResponseEntity<ApiResponse<?>> response) {
        ApiResponse<StartSessionResponse> envelope =
                (ApiResponse<StartSessionResponse>) response.getBody();
        assertNotNull(envelope);
        return envelope.getData();
    }

    // ── 1. Backward compat — clientId omitted ──────────────────────────

    @Test
    void clientIdOmitted_serverGeneratesIdAndBehavesAsBefore() {
        ArgumentCaptor<WorkoutSession> captor = ArgumentCaptor.forClass(WorkoutSession.class);

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(null), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(sessionRepo).saveAndFlush(captor.capture());
        UUID assignedId = captor.getValue().getId();
        assertNotNull(assignedId, "server must assign an id when clientId is omitted");
        assertNotEquals(ZERO_UUID, assignedId);

        // findById must NOT be called when clientId is null
        verify(sessionRepo, never()).findById(any());
        assertEquals(assignedId, body(response).sessionId());
    }

    // ── 2. clientId provided, no existing session → INSERT with that id ─

    @Test
    void clientIdProvided_noExistingSession_insertsWithThatId() {
        ArgumentCaptor<WorkoutSession> captor = ArgumentCaptor.forClass(WorkoutSession.class);

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(sessionRepo).findById(CLIENT_ID);
        verify(sessionRepo).saveAndFlush(captor.capture());
        assertEquals(CLIENT_ID, captor.getValue().getId());
        assertEquals(CLIENT_ID, body(response).sessionId());
    }

    // ── 3. Idempotent retry — second call returns same session ─────────

    @Test
    void clientIdProvidedTwice_secondCallReturnsExistingNoNewRow() {
        WorkoutSession existing = session(CLIENT_ID, USER_ID, "IN_PROGRESS");
        when(sessionRepo.findById(CLIENT_ID)).thenReturn(Optional.of(existing));

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(CLIENT_ID, body(response).sessionId());
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    // ── 4. clientId matches a COMPLETED session → 409 ──────────────────

    @Test
    void clientIdMatchesCompletedSession_returns409SessionAlreadyFinalized() {
        WorkoutSession completed = session(CLIENT_ID, USER_ID, "COMPLETED");
        when(sessionRepo.findById(CLIENT_ID)).thenReturn(Optional.of(completed));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.startSession(reqWithClientId(CLIENT_ID), auth));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("SESSION_ALREADY_FINALIZED", ex.getCode());
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    // ── 5. clientId belongs to another user → 404 (don't leak existence) ─

    @Test
    void clientIdBelongsToAnotherUser_returns404NotFound() {
        WorkoutSession foreign = session(CLIENT_ID, OTHER_USER_ID, "IN_PROGRESS");
        when(sessionRepo.findById(CLIENT_ID)).thenReturn(Optional.of(foreign));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.startSession(reqWithClientId(CLIENT_ID), auth));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus(),
                "cross-user clientId must look like a missing row to avoid leaking existence");
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    // ── 6. Concurrent INSERT race for same clientId — race recovery ────

    @Test
    @Disabled("Concurrency-test infrastructure not in place — logic written for the " +
              "race-recovery path: two threads call /start with the same clientId, both " +
              "find no existing row, both call saveAndFlush; one INSERT trips the PK " +
              "constraint and DataIntegrityViolationException is thrown. The catch block " +
              "must re-query findById and return the winner's session. Re-enable when " +
              "real-DB integration tests are added.")
    void concurrentInsertSameClientId_loserReturnsWinnersSession() {
        WorkoutSession winner = session(CLIENT_ID, USER_ID, "IN_PROGRESS");
        // Loser sees no row in the idempotency check, then trips PK on save
        when(sessionRepo.saveAndFlush(any(WorkoutSession.class)))
                .thenThrow(new DataIntegrityViolationException("PK violation"));
        // After the race, re-query finds the winner
        when(sessionRepo.findById(CLIENT_ID))
                .thenReturn(Optional.empty())  // first call: idempotency check
                .thenReturn(Optional.of(winner)); // second call: race recovery

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(CLIENT_ID, body(response).sessionId());
        verify(sessionRepo, times(2)).findById(CLIENT_ID);
    }

    // Single-threaded simulation of the race-recovery branch — exercised
    // here because it doesn't need parallel-test infra: we drive the
    // sequence of mocked responses directly.
    @Test
    void raceRecovery_pkViolationThenReQueryReturnsExistingSession() {
        WorkoutSession winner = session(CLIENT_ID, USER_ID, "IN_PROGRESS");
        when(sessionRepo.findById(CLIENT_ID))
                .thenReturn(Optional.empty())     // idempotency lookup misses
                .thenReturn(Optional.of(winner)); // race-recovery lookup hits
        when(sessionRepo.saveAndFlush(any(WorkoutSession.class)))
                .thenThrow(new DataIntegrityViolationException("PK violation"));

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(CLIENT_ID, body(response).sessionId());
        verify(sessionRepo, times(2)).findById(CLIENT_ID);
        verify(sessionRepo).saveAndFlush(any());
    }

    // ── 7. Malformed UUID — framework-level rejection ──────────────────

    @Test
    @Disabled("Malformed UUIDs are rejected by Jackson at deserialization time " +
              "(HTTP 400 BAD_REQUEST via the global exception handler). The controller " +
              "method signature accepts UUID, so by the time the handler runs the value " +
              "is already a valid UUID instance. Covered by integration tests that hit " +
              "the HTTP layer; not exercisable in this pure unit test.")
    void malformedClientId_rejectedByJackson() {}

    // ── 8. All-zeros UUID → 400 ────────────────────────────────────────

    @Test
    void allZeroClientId_returns400InvalidClientId() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.startSession(reqWithClientId(ZERO_UUID), auth));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("INVALID_CLIENT_ID", ex.getCode());
        verify(sessionRepo, never()).findById(any());
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    // ── 9. clientId + cooldown active → cooldown wins ──────────────────

    @Test
    void cooldownTakesPrecedenceOverClientId() {
        WorkoutSession recent = session(UUID.randomUUID(), USER_ID, "COMPLETED");
        recent.setFinishedAt(Instant.now().minusSeconds(60));
        when(sessionRepo.findFirstByUserIdAndStatusAndFinishedAtAfter(eq(USER_ID), eq("COMPLETED"), any()))
                .thenReturn(Optional.of(recent));

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ApiResponse<?> envelope = response.getBody();
        assertNotNull(envelope);
        assertNotNull(envelope.getError());
        assertEquals("SESSION_TOO_SOON", envelope.getError().getCode());

        // No idempotency lookup, no save — cooldown short-circuits everything
        verify(sessionRepo, never()).findById(any());
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    // ── 10. clientId + different IN_PROGRESS session → return existing IP ─

    @Test
    void inProgressSessionWithDifferentId_takesPrecedenceOverFreshClientId() {
        UUID existingInProgressId = UUID.fromString("99999999-9999-4999-8999-999999999999");
        WorkoutSession ip = session(existingInProgressId, USER_ID, "IN_PROGRESS");
        when(sessionRepo.findFirstByUserIdAndStatusOrderByStartedAtDesc(eq(USER_ID), eq("IN_PROGRESS")))
                .thenReturn(Optional.of(ip));

        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(existingInProgressId, body(response).sessionId(),
                "fresh clientId must NOT clobber an existing in-progress session");
        // clientId lookup happens (and returns empty), but no save
        verify(sessionRepo).findById(CLIENT_ID);
        verify(sessionRepo, never()).saveAndFlush(any());
    }

    // ── Tiny additional sanity test: response envelope shape ───────────

    @Test
    void successResponseHasCorrectEnvelopeShape() {
        ResponseEntity<ApiResponse<?>> response = controller.startSession(reqWithClientId(CLIENT_ID), auth);
        ApiResponse<?> envelope = response.getBody();
        assertNotNull(envelope);
        assertNotNull(envelope.getData());
        assertTrue(envelope.getError() == null);
    }

    // ── Persistable contract: kills the SELECT-before-INSERT round-trip ─

    @Test
    void assignedIdInsert_doesNotIssueSelectBeforeInsert() {
        // Spring Data JPA's save() consults Persistable.isNew() before
        // choosing between EntityManager.persist (single INSERT) and
        // EntityManager.merge (SELECT + INSERT/UPDATE). For a fresh
        // WorkoutSession with a caller-assigned id, isNew() must report
        // true so the merge path's existence-check SELECT is skipped.
        //
        // Mockito on a JpaRepository proxy can't observe the SQL
        // Hibernate emits — the merge SELECT goes through EntityManager
        // directly, not the repo. We therefore assert the Persistable
        // contract that drives Hibernate's choice:
        //   1. A brand-new entity reports isNew() = true.
        //   2. Setting an assigned id does not flip the flag.
        //   3. After @PrePersist fires, isNew() = false (subsequent
        //      saves of the same instance correctly pick the merge path).
        //   4. After @PostLoad fires, isNew() = false (loaded entities
        //      are not new).
        //
        // SQL-level verification belongs in a real-DB integration test,
        // tracked as a follow-up alongside Stage 2.

        WorkoutSession fresh = new WorkoutSession();
        assertTrue(fresh.isNew(),
                "fresh entity must be isNew=true so Spring Data picks persist over merge");

        fresh.setId(CLIENT_ID);
        assertTrue(fresh.isNew(),
                "assigning an id must not flip isNew (merge would re-emerge as the chosen path)");

        // Simulate the JPA lifecycle callbacks Hibernate would fire.
        // The package-private accessors aren't reachable from this test
        // package, so drive the same field via the public contract by
        // routing through reflection-free fixtures.
        WorkoutSession loaded = session(UUID.randomUUID(), USER_ID, "IN_PROGRESS");
        invokePostLoad(loaded);
        assertTrue(!loaded.isNew(),
                "after @PostLoad fires, a loaded entity must be isNew=false");

        WorkoutSession persisted = new WorkoutSession();
        persisted.setId(UUID.randomUUID());
        invokePrePersist(persisted);
        assertTrue(!persisted.isNew(),
                "after @PrePersist fires, a persisted entity must be isNew=false");
    }

    private static void invokePrePersist(WorkoutSession s) {
        try {
            var m = WorkoutSession.class.getDeclaredMethod("prePersist");
            m.setAccessible(true);
            m.invoke(s);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("prePersist callback missing or unreachable", e);
        }
    }

    private static void invokePostLoad(WorkoutSession s) {
        try {
            var m = WorkoutSession.class.getDeclaredMethod("postLoad");
            m.setAccessible(true);
            m.invoke(s);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("postLoad callback missing or unreachable", e);
        }
    }
}
