package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.jobs.JobEnqueuer;
import com.fittribe.api.jobs.JobType;
import com.fittribe.api.jobs.JobWorker;
import com.fittribe.api.jobs.WeeklyReportCron;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminJobTriggerController}. The repository is
 * a JPA interface (mockable); {@link JobEnqueuer} and
 * {@link WeeklyReportCron} are concrete classes that Mockito on JDK 25
 * can't instrument, so we use hand-rolled fakes — same pattern
 * established in {@code JobWorkerTest} and {@code WeeklyReportCronTest}.
 *
 * <p>Exceptions thrown by the controller flow through
 * {@link ApiException}, which would normally be converted to a
 * response by {@code GlobalExceptionHandler}. In unit tests we don't
 * spin up MVC, so we assert on the exception directly and read its
 * {@link HttpStatus} / code — the envelope conversion is covered by
 * the handler's own suite.
 */
class AdminJobTriggerControllerTest {

    private static final String CONFIGURED_SECRET = "correct-horse-battery-staple";

    private UserRepository userRepo;
    private FakeJobEnqueuer enqueuer;
    private FakeWeeklyReportCron cron;
    private AdminJobTriggerController controller;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        enqueuer = new FakeJobEnqueuer();
        cron = new FakeWeeklyReportCron();
        controller = new AdminJobTriggerController(userRepo, enqueuer, cron, CONFIGURED_SECRET);
    }

    // ── Secret validation ──────────────────────────────────────────────

    @Test
    void missingSecretHeaderIs401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport(null, new AdminJobTriggerController.TriggerRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertTrue(enqueuer.calls.isEmpty(), "no enqueue on auth failure");
        assertEquals(0, cron.fanOutCalls, "no fan-out on auth failure");
    }

    @Test
    void emptySecretHeaderIs401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport("", new AdminJobTriggerController.TriggerRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void wrongSecretHeaderIs401() {
        ApiException ex = assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport("not-the-secret", new AdminJobTriggerController.TriggerRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void emptyConfiguredSecretRejectsEverything() {
        // Controller constructed with empty secret — deny-by-default
        // even if the header matches exactly.
        AdminJobTriggerController denyAll = new AdminJobTriggerController(userRepo, enqueuer, cron, "");

        ApiException ex = assertThrows(ApiException.class,
                () -> denyAll.triggerWeeklyReport("any-value", new AdminJobTriggerController.TriggerRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertTrue(enqueuer.calls.isEmpty());
        assertEquals(0, cron.fanOutCalls);
    }

    @Test
    void whitespaceConfiguredSecretRejectsEverything() {
        AdminJobTriggerController denyAll = new AdminJobTriggerController(userRepo, enqueuer, cron, "   ");
        assertThrows(ApiException.class,
                () -> denyAll.triggerWeeklyReport("   ", new AdminJobTriggerController.TriggerRequest()));
    }

    // ── Single-user path ───────────────────────────────────────────────

    @Test
    void validUserIdEnqueuesBothJobs() {
        UUID userId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        User user = activeUser(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();
        body.userId = userId.toString();

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.triggerWeeklyReport(CONFIGURED_SECRET, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        assertEquals(2, ((Number) response.getBody().getData().get("enqueued")).intValue(),
                "should enqueue 2 jobs: COMPUTE_WEEKLY_REPORT and COMPUTE_STRENGTH_PROGRESSION");

        // Enqueued exactly two rows with the right shape
        assertEquals(2, enqueuer.calls.size());
        FakeJobEnqueuer.Call c0 = enqueuer.calls.get(0);
        assertEquals(JobType.COMPUTE_WEEKLY_REPORT, c0.type);
        assertEquals(userId.toString(), c0.payload.get("userId"));
        assertEquals(JobWorker.previousMondayIst().toString(), c0.payload.get("weekStart"));
        assertEquals(2, c0.payload.size(), "payload must contain only userId + weekStart");

        FakeJobEnqueuer.Call c1 = enqueuer.calls.get(1);
        assertEquals(JobType.COMPUTE_STRENGTH_PROGRESSION, c1.type);
        assertEquals(userId.toString(), c1.payload.get("userId"));
        assertEquals(JobWorker.previousMondayIst().toString(), c1.payload.get("weekStart"));
        assertEquals(2, c1.payload.size(), "payload must contain only userId + weekStart");

        // Fan-out path must NOT have been touched
        assertEquals(0, cron.fanOutCalls);
    }

    @Test
    void badUuidIs400() {
        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();
        body.userId = "not-a-uuid";

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport(CONFIGURED_SECRET, body));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
        assertTrue(enqueuer.calls.isEmpty());
        assertEquals(0, cron.fanOutCalls);
    }

    @Test
    void unknownUserIs404() {
        UUID userId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000999");
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();
        body.userId = userId.toString();

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport(CONFIGURED_SECRET, body));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("NOT_FOUND", ex.getCode());
        assertTrue(enqueuer.calls.isEmpty());
    }

    @Test
    void inactiveUserIs404() {
        UUID userId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
        User user = activeUser(userId);
        user.setIsActive(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();
        body.userId = userId.toString();

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport(CONFIGURED_SECRET, body));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus(),
                "inactive user must look the same as a missing one to an admin caller");
        assertTrue(enqueuer.calls.isEmpty());
    }

    @Test
    void deletionRequestedUserIs404() {
        UUID userId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
        User user = activeUser(userId);
        user.setDeletionRequestedAt(Instant.now());
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();
        body.userId = userId.toString();

        assertThrows(ApiException.class,
                () -> controller.triggerWeeklyReport(CONFIGURED_SECRET, body));
        assertTrue(enqueuer.calls.isEmpty());
    }

    // ── All-active-users path ──────────────────────────────────────────

    @Test
    void missingUserIdDelegatesToCronFanOut() {
        cron.fanOutResult = 7;

        // Body is present but userId is null
        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.triggerWeeklyReport(CONFIGURED_SECRET, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(7, ((Number) response.getBody().getData().get("enqueued")).intValue());
        assertEquals(1, cron.fanOutCalls, "fan-out called exactly once");
        assertTrue(enqueuer.calls.isEmpty(), "controller must not duplicate the fan-out work");
    }

    @Test
    void nullBodyAlsoDelegatesToCronFanOut() {
        cron.fanOutResult = 0;

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.triggerWeeklyReport(CONFIGURED_SECRET, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Number) response.getBody().getData().get("enqueued")).intValue());
        assertEquals(1, cron.fanOutCalls);
    }

    @Test
    void blankUserIdAlsoDelegatesToCronFanOut() {
        cron.fanOutResult = 3;

        AdminJobTriggerController.TriggerRequest body = new AdminJobTriggerController.TriggerRequest();
        body.userId = "   ";

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.triggerWeeklyReport(CONFIGURED_SECRET, body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, ((Number) response.getBody().getData().get("enqueued")).intValue());
        assertEquals(1, cron.fanOutCalls);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static User activeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setIsActive(true);
        u.setDeletionRequestedAt(null);
        return u;
    }

    // ── Hand-rolled fakes (Mockito JDK 25 can't instrument) ────────────

    static final class FakeJobEnqueuer extends JobEnqueuer {
        final List<Call> calls = new ArrayList<>();
        FakeJobEnqueuer() { super(null, new ObjectMapper()); }
        @Override
        public Long enqueue(JobType type, Map<String, Object> payload) {
            calls.add(new Call(type, Map.copyOf(payload)));
            return (long) calls.size();
        }
        static final class Call {
            final JobType type;
            final Map<String, Object> payload;
            Call(JobType type, Map<String, Object> payload) {
                this.type = type;
                this.payload = payload;
            }
        }
    }

    static final class FakeWeeklyReportCron extends WeeklyReportCron {
        int fanOutResult;
        int fanOutCalls;
        FakeWeeklyReportCron() { super(null, null); }
        @Override
        public int fanOutSundayJobs() {
            fanOutCalls++;
            return fanOutResult;
        }
    }
}
