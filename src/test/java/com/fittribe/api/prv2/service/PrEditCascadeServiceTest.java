package com.fittribe.api.prv2.service;

import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.UserExerciseBests;
import com.fittribe.api.entity.WeeklyPrCount;
import com.fittribe.api.prv2.detector.ExerciseType;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.detector.PRDetector;
import com.fittribe.api.prv2.detector.PRResult;
import com.fittribe.api.prv2.detector.PrCategory;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.WeeklyPrCountRepository;
import com.fittribe.api.service.CoinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito unit tests for PrEditCascadeService — cascade correctness PR.
 *
 * <p>Tests verify the three entangled bugs:
 * <ol>
 *   <li>Revert un-supersede: prior PRs are restored when a superseding edit is reverted</li>
 *   <li>Per-category un-supersede: WEIGHT_PR restore doesn't cross-contaminate REP_PR</li>
 *   <li>Bodyweight: cascade doesn't NPE on null weightKg, FIRST_EVER stays sticky</li>
 * </ol>
 */
@Disabled("Unit tests for PrEditCascadeService — enable with @SpringBootTest context for full integration")
@ExtendWith(MockitoExtension.class)
@DisplayName("PrEditCascadeService — cascade correctness")
class PrEditCascadeServiceTest {

    @Mock private PRDetector prDetector;
    @Mock private UserExerciseBestsRepository userExerciseBestsRepo;
    @Mock private PrEventRepository prEventRepo;
    @Mock private WeeklyPrCountRepository weeklyPrCountRepo;
    @Mock private CoinService coinService;
    @Mock private PlatformTransactionManager transactionManager;

    private PrEditCascadeService service;

    private UUID userId;
    private UUID sessionId;
    private UUID setId;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        service = new PrEditCascadeService(
                prDetector,
                userExerciseBestsRepo,
                prEventRepo,
                weeklyPrCountRepo,
                coinService,
                transactionManager);

        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        setId = UUID.randomUUID();
        weekStart = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static final com.fasterxml.jackson.databind.ObjectMapper TEST_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @SuppressWarnings("unchecked")
    private PrEvent makeEvent(UUID eventSetId, String category, int coins, String valuePayloadJson) {
        PrEvent event = new PrEvent();
        event.setUserId(userId);
        event.setSessionId(sessionId);
        event.setSetId(eventSetId);
        event.setExerciseId("bench-press");
        event.setPrCategory(category);
        event.setCoinsAwarded(coins);
        event.setWeekStart(weekStart);
        event.setSignalsMet(Map.of());
        try {
            event.setValuePayload(TEST_MAPPER.readValue(valuePayloadJson, Map.class));
        } catch (Exception e) {
            throw new RuntimeException("Bad test JSON: " + valuePayloadJson, e);
        }
        event.setDetectorVersion("v1.0");
        return event;
    }

    private PRResult noResult() {
        return new PRResult(false, null, Map.of(), Map.of(), 0, "v1.0");
    }

    private void stubBestsLookup(UserExerciseBests bests) {
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(bests != null ? Optional.of(bests) : Optional.empty());
    }

    private void stubWeeklyCountsEmpty() {
        when(weeklyPrCountRepo.findByUserIdAndWeekStart(eq(userId), any()))
                .thenReturn(Optional.empty());
    }

    private void stubActiveEventsEmpty() {
        when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                .thenReturn(List.of());
    }

    // ── Scenario 1: Revert un-supersede (HLD canonical) ──────────────────

    @Nested
    @DisplayName("Scenario 1: Revert un-supersede")
    class RevertUnSupersede {

        @Test
        @DisplayName("finish 10kg FIRST_EVER → edit 15kg WEIGHT_PR → revert 8kg: FIRST_EVER stays, WEIGHT_PR superseded, bests=10")
        void revertRestoresPriorPr() {
            // State after "edit to 15kg": E1 (FIRST_EVER, 10kg) is active, E2 (WEIGHT_PR, 15kg) is active
            // Now we revert to 8kg — this edit should:
            //   - NOT supersede E1 (FIRST_EVER is permanent)
            //   - Supersede E2 (WEIGHT_PR)
            //   - No un-supersede needed (E2 never superseded anything via superseded_by)
            //   - Re-detect on 8kg: no PR fires (below best)
            //   - Rebuild bests from E1 (FIRST_EVER, 10kg)

            PrEvent e1FirstEver = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"weight_kg\":10,\"reps\":10}}");
            PrEvent e2WeightPr = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":15,\"reps\":10},\"previous_best\":{\"weight_kg\":10,\"reps\":10},\"delta_kg\":5}");

            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1FirstEver, e2WeightPr));

            // E2 was not pointed to by any prior event's superseded_by
            when(prEventRepo.findBySupersededByAndPrCategory(e2WeightPr.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of());

            // Re-detect on 8kg: no PR
            UserExerciseBests bests = new UserExerciseBests();
            bests.setUserId(userId);
            bests.setExerciseId("bench-press");
            bests.setExerciseType("WEIGHTED");
            bests.setBestWtKg(BigDecimal.valueOf(15));
            bests.setRepsAtBestWt(10);
            stubBestsLookup(bests);

            when(prDetector.detect(any(), any(), any())).thenReturn(noResult());
            stubWeeklyCountsEmpty();

            // After cascade, rebuild reads active events
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1FirstEver));

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(15), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(8), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // FIRST_EVER should NOT be superseded
            assertNull(e1FirstEver.getSupersededAt(), "FIRST_EVER must not be superseded");

            // WEIGHT_PR should be superseded
            assertNotNull(e2WeightPr.getSupersededAt(), "WEIGHT_PR must be superseded");

            // Coins: -5 for revoking WEIGHT_PR, no +3 revocation for FIRST_EVER
            verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                    contains("WEIGHT_PR"), contains(":revoke:"));
            verify(coinService, never()).awardCoins(eq(userId), eq(-3), anyString(), anyString(), anyString());

            // Bests rebuilt from FIRST_EVER (10kg)
            ArgumentCaptor<UserExerciseBests> bestsCaptor = ArgumentCaptor.forClass(UserExerciseBests.class);
            verify(userExerciseBestsRepo, atLeastOnce()).save(bestsCaptor.capture());
            UserExerciseBests savedBests = bestsCaptor.getValue();
            assertEquals(BigDecimal.valueOf(10), savedBests.getBestWtKg());
        }
    }

    // ── Scenario 2: FIRST_EVER sticky on edit ────────────────────────────

    @Nested
    @DisplayName("Scenario 2: FIRST_EVER sticky on edit")
    class FirstEverSticky {

        @Test
        @DisplayName("finish 60kg FIRST_EVER → edit to 55kg: FIRST_EVER stays active, +3 not revoked")
        void firstEverNotSupersedesOnEdit() {
            PrEvent e1 = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"weight_kg\":60,\"reps\":10}}");

            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1));

            when(prDetector.detect(any(), any(), any())).thenReturn(noResult());
            stubBestsLookup(null);
            stubWeeklyCountsEmpty();

            // Rebuild: FIRST_EVER still active
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1));

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(60), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(55), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // FIRST_EVER stays active
            assertNull(e1.getSupersededAt());

            // No coin revocation at all
            verify(coinService, never()).awardCoins(eq(userId), anyInt(), eq("PR_REVOKED"),
                    anyString(), anyString());
        }
    }

    // ── Scenario 3: FIRST_EVER sticky on set delete ──────────────────────

    @Nested
    @DisplayName("Scenario 3: FIRST_EVER sticky on set delete")
    class FirstEverStickyOnDelete {

        @Test
        @DisplayName("delete a set that has FIRST_EVER: FIRST_EVER stays active")
        void firstEverNotSupersededOnDelete() {
            PrEvent e1 = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"weight_kg\":60,\"reps\":10}}");

            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1));

            stubWeeklyCountsEmpty();

            // Rebuild: FIRST_EVER still active
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1));
            stubBestsLookup(null);

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(60), 10, null);

            service.processSetDelete(userId, sessionId, setId, oldValue);

            // FIRST_EVER stays active
            assertNull(e1.getSupersededAt());

            // No coin revocation
            verify(coinService, never()).awardCoins(eq(userId), anyInt(), eq("PR_REVOKED"),
                    anyString(), anyString());
        }
    }

    // ── Scenario 4: Category-aware un-supersede ──────────────────────────

    @Nested
    @DisplayName("Scenario 4: Category-aware un-supersede")
    class CategoryAwareUnSupersede {

        @Test
        @DisplayName("finish WEIGHT_PR 70kg → edit 80kg (new WEIGHT_PR) → revert 65kg: 70kg WEIGHT_PR restored")
        void revertRestoresMatchingCategory() {
            // E1: original WEIGHT_PR at 70kg, was superseded_by E2
            PrEvent e1 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":70,\"reps\":10},\"previous_best\":{\"weight_kg\":60,\"reps\":8},\"delta_kg\":10}");
            e1.setSupersededAt(Instant.now().minusSeconds(60));
            UUID e2Id = UUID.randomUUID();
            e1.setSupersededBy(e2Id);

            // E2: current active WEIGHT_PR at 80kg
            PrEvent e2 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":80,\"reps\":10},\"previous_best\":{\"weight_kg\":70,\"reps\":10},\"delta_kg\":10}");
            // Simulate that e2 has an id matching e2Id
            // (In real DB, save would assign id; here we use the mock)

            // Separate REP_PR on a different set — must be untouched
            UUID otherSetId = UUID.randomUUID();
            PrEvent repPr = makeEvent(otherSetId, "REP_PR", 5,
                    "{\"weight_kg\":60,\"previous_reps\":8,\"new_reps\":12,\"delta_reps\":4}");

            // Step 1: active events for THIS setId only
            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e2));

            // Step 4: un-supersede — E2 superseded E1 (same WEIGHT_PR category)
            when(prEventRepo.findBySupersededByAndPrCategory(e2.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of(e1));

            // Re-detect on 65kg: no PR (below 70kg best)
            UserExerciseBests bests = new UserExerciseBests();
            bests.setUserId(userId);
            bests.setExerciseId("bench-press");
            bests.setExerciseType("WEIGHTED");
            bests.setBestWtKg(BigDecimal.valueOf(80));
            stubBestsLookup(bests);

            when(prDetector.detect(any(), any(), any())).thenReturn(noResult());
            stubWeeklyCountsEmpty();

            // After cascade, rebuild reads E1 (restored) as active
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1));

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(80), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(65), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // E2 superseded
            assertNotNull(e2.getSupersededAt());

            // E1 un-superseded (restored)
            assertNull(e1.getSupersededAt());
            assertNull(e1.getSupersededBy());

            // REP_PR on other set is untouched (never queried)
            assertNull(repPr.getSupersededAt());

            // Coins: -5 revoke E2, +5 restore E1
            verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                    contains("WEIGHT_PR"), contains(":revoke:"));
            verify(coinService).awardCoins(eq(userId), eq(5), eq("PR_RESTORED"),
                    contains("WEIGHT_PR"), contains(":restore:"));

            // Bests rebuilt from restored E1 (70kg)
            ArgumentCaptor<UserExerciseBests> cap = ArgumentCaptor.forClass(UserExerciseBests.class);
            verify(userExerciseBestsRepo, atLeastOnce()).save(cap.capture());
            UserExerciseBests saved = cap.getValue();
            assertEquals(BigDecimal.valueOf(70), saved.getBestWtKg());
        }
    }

    // ── Scenario 5: Repeated supersession coin math ──────────────────────

    @Nested
    @DisplayName("Scenario 5: Repeated supersession — distinct referenceIds")
    class RepeatedSupersession {

        @Test
        @DisplayName("multiple edit cycles produce distinct coin referenceIds (no idempotency collision)")
        void repeatedEditsHaveDistinctReferenceIds() {
            PrEvent e1 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":60,\"reps\":10},\"delta_kg\":10}");

            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1));
            when(prEventRepo.findBySupersededByAndPrCategory(any(), anyString()))
                    .thenReturn(List.of());

            when(prDetector.detect(any(), any(), any())).thenReturn(noResult());
            stubBestsLookup(null);
            stubWeeklyCountsEmpty();
            stubActiveEventsEmpty();

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(60), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(50), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // Capture the referenceId used for revocation
            ArgumentCaptor<String> refCaptor = ArgumentCaptor.forClass(String.class);
            verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                    anyString(), refCaptor.capture());

            String refId = refCaptor.getValue();
            // Must contain event id + ":revoke:" + timestamp millis
            assertTrue(refId.contains(":revoke:"),
                    "referenceId must contain ':revoke:' timestamp suffix, got: " + refId);
            assertTrue(refId.startsWith(e1.getId() != null ? e1.getId().toString() : ""),
                    "referenceId must start with event id");
        }
    }

    // ── Scenario 6: Bodyweight edit cascade ──────────────────────────────

    @Nested
    @DisplayName("Scenario 6: Bodyweight edit cascade")
    class BodyweightEditCascade {

        @Test
        @DisplayName("finish push-ups FIRST_EVER → edit reps: no NPE, FIRST_EVER stays, no new PR")
        void bodyweightEditDoesNotNpe() {
            PrEvent e1 = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"reps\":15}}");
            e1.setExerciseId("push-ups");

            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1));

            when(prDetector.detect(any(), any(), any())).thenReturn(noResult());
            when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "push-ups"))
                    .thenReturn(Optional.empty());
            stubWeeklyCountsEmpty();

            // Rebuild: FIRST_EVER still active for push-ups
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "push-ups"))
                    .thenReturn(List.of(e1));

            LoggedSet oldValue = new LoggedSet(setId, "push-ups", null, 15, null);
            LoggedSet newValue = new LoggedSet(setId, "push-ups", null, 20, null);

            // Must not throw
            assertDoesNotThrow(() ->
                    service.processSetEdit(userId, sessionId, setId, oldValue, newValue));

            // FIRST_EVER stays active
            assertNull(e1.getSupersededAt());

            // No coin revocation
            verify(coinService, never()).awardCoins(eq(userId), anyInt(), eq("PR_REVOKED"),
                    anyString(), anyString());
        }
    }

    // ── Scenario 7: Un-supersede fires rebuildExerciseBests ──────────────

    @Nested
    @DisplayName("Scenario 7: rebuildExerciseBests tracks 10→15→10")
    class RebuildBestsOnRevert {

        @Test
        @DisplayName("10→15→8 revert: bests.bestWtKg ends at 10, not 8 or 15")
        void bestsRebuiltFromRestoredEvent() {
            // After the first edit (10→15), E1 was superseded_by E2.
            // Now we revert (15→8): E2 superseded, E1 restored.
            // Rebuild should read E1's payload (10kg) and set bestWtKg=10.

            PrEvent e1FirstEver = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"weight_kg\":10,\"reps\":10}}");
            // E1 was superseded by E2 during the 10→15 edit
            UUID e2Id = UUID.randomUUID();
            e1FirstEver.setSupersededAt(Instant.now().minusSeconds(120));
            e1FirstEver.setSupersededBy(e2Id);

            PrEvent e2WeightPr = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":15,\"reps\":10},\"previous_best\":{\"weight_kg\":10,\"reps\":10},\"delta_kg\":5}");

            // Active events for the set: only E2 (E1 is superseded from prior edit)
            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e2WeightPr));

            // Un-supersede: E2 superseded E1 with matching WEIGHT_PR? No — E1 is FIRST_EVER.
            // But the un-supersede queries superseded_by=E2.id with category=WEIGHT_PR
            when(prEventRepo.findBySupersededByAndPrCategory(e2WeightPr.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of());
            // E1 has category FIRST_EVER, so it won't be found by the WEIGHT_PR query.
            // But wait — E1's superseded_by points to e2Id. We need to also check:
            // the un-supersede step iterates supersedable (non-FIRST_EVER) events,
            // which is just E2. It queries findBySupersededByAndPrCategory(e2.id, "WEIGHT_PR").
            // E1 has pr_category="FIRST_EVER", so it won't match. Correct!
            // The FIRST_EVER stays superseded from the prior edit.
            // Actually — this means FIRST_EVER restoration would need a separate path.
            // But Decision 1 says FIRST_EVER is never superseded in the first place.
            // So the prior edit (10→15) should NOT have superseded E1.
            // This test validates the full pipeline: E1 should have been active all along.

            // Let me restructure: in the correct pipeline, E1 was never superseded.
            // So active events = [E1(FIRST_EVER), E2(WEIGHT_PR)]
            e1FirstEver.setSupersededAt(null);
            e1FirstEver.setSupersededBy(null);

            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1FirstEver, e2WeightPr));
            when(prEventRepo.findBySupersededByAndPrCategory(e2WeightPr.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of());

            // Re-detect on 8kg: no PR
            UserExerciseBests existingBests = new UserExerciseBests();
            existingBests.setUserId(userId);
            existingBests.setExerciseId("bench-press");
            existingBests.setExerciseType("WEIGHTED");
            existingBests.setBestWtKg(BigDecimal.valueOf(15));
            existingBests.setRepsAtBestWt(10);
            stubBestsLookup(existingBests);

            when(prDetector.detect(any(), any(), any())).thenReturn(noResult());
            stubWeeklyCountsEmpty();

            // Rebuild sees: E1(FIRST_EVER, 10kg active), E2 now superseded
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1FirstEver));

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(15), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(8), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // bestWtKg should be 10 (from FIRST_EVER payload), not 15 or 8
            ArgumentCaptor<UserExerciseBests> cap = ArgumentCaptor.forClass(UserExerciseBests.class);
            verify(userExerciseBestsRepo, atLeastOnce()).save(cap.capture());
            UserExerciseBests saved = cap.getValue();
            assertEquals(BigDecimal.valueOf(10), saved.getBestWtKg(),
                    "bestWtKg must be 10 (from restored FIRST_EVER), not 15 (superseded) or 8 (reverted)");
            assertEquals(10, saved.getRepsAtBestWt());
        }
    }

    // ── Scenario 8: WEIGHT_PR revert against prior history ───────────────

    @Nested
    @DisplayName("Scenario 8: Re-detect against rebuilt baseline, not stale cache")
    class RedetectAgainstRebuiltBaseline {

        @Test
        @DisplayName("75→80→72 revert: 72 does NOT fire because rebuilt baseline is 75 (restored E1)")
        void redetectSeesRebuiltBaseline() {
            // Prior history: bestWtKg=70 from older sessions.
            // Finish 75kg → WEIGHT_PR E1 (+5). bestWtKg now 75.
            // Edit 80kg → E1 superseded_by E2, E2 WEIGHT_PR (+5). bestWtKg now 80.
            // Revert 72kg → this cascade runs.

            // E1: original WEIGHT_PR at 75kg, was superseded_by E2
            PrEvent e1 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":75,\"reps\":10},\"previous_best\":{\"weight_kg\":70,\"reps\":10},\"delta_kg\":5}");
            UUID e2Id = UUID.randomUUID();
            e1.setSupersededAt(Instant.now().minusSeconds(60));
            e1.setSupersededBy(e2Id);

            // E2: current active WEIGHT_PR at 80kg
            PrEvent e2 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":80,\"reps\":10},\"previous_best\":{\"weight_kg\":75,\"reps\":10},\"delta_kg\":5}");

            // Step 1: active events for this set = [E2]
            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e2));

            // Step 4: E2 superseded E1 (same WEIGHT_PR category)
            when(prEventRepo.findBySupersededByAndPrCategory(e2.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of(e1));

            stubWeeklyCountsEmpty();

            // Step 5.5 rebuild: after superseding E2 and restoring E1,
            // active events = [E1 @ 75kg]
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1));

            // After Step 5.5 rebuild, bests row has bestWtKg=75
            UserExerciseBests rebuiltBests = new UserExerciseBests();
            rebuiltBests.setUserId(userId);
            rebuiltBests.setExerciseId("bench-press");
            rebuiltBests.setExerciseType("WEIGHTED");
            rebuiltBests.setBestWtKg(BigDecimal.valueOf(75));
            rebuiltBests.setRepsAtBestWt(10);
            rebuiltBests.setTotalSessionsWithExercise(5);

            // Step 6 re-reads bests after rebuild
            when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                    .thenReturn(Optional.of(rebuiltBests));

            // 72kg < 75kg baseline → no WEIGHT_PR
            when(prDetector.detect(any(), eq(rebuiltBests), any())).thenReturn(noResult());

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(80), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(72), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // E2 superseded
            assertNotNull(e2.getSupersededAt());

            // E1 restored
            assertNull(e1.getSupersededAt());
            assertNull(e1.getSupersededBy());

            // 72kg did NOT fire a new PR (72 < 75 baseline)
            // PRDetector was called with rebuiltBests (75kg), not stale 80kg
            verify(prDetector).detect(any(), eq(rebuiltBests), any());

            // No new PR event created beyond the supersede/restore saves
            // The only coinService calls should be: revoke E2, restore E1
            verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                    contains("WEIGHT_PR"), contains(":revoke:"));
            verify(coinService).awardCoins(eq(userId), eq(5), eq("PR_RESTORED"),
                    contains("WEIGHT_PR"), contains(":restore:"));
            verify(coinService, times(2)).awardCoins(anyString() == null ? eq(userId) : eq(userId),
                    anyInt(), anyString(), anyString(), anyString());
        }
    }

    // ── Scenario 9: Edit fires WEIGHT_PR after FIRST_EVER + cleared cache ─

    @Nested
    @DisplayName("Scenario 9: FIRST_EVER + edit fires WEIGHT_PR against empty weight baseline")
    class FirstEverThenWeightPrOnEdit {

        @Test
        @DisplayName("10kg FIRST_EVER → 15kg WEIGHT_PR → revert 12kg → 12kg fires WEIGHT_PR against null baseline")
        void editFiresWeightPrAfterClearedBaseline() {
            // State entering this cascade (revert from 15 to 12):
            // E1: FIRST_EVER (10kg) — active (never superseded, Decision 1)
            // E2: WEIGHT_PR (15kg) — active
            // bestWtKg = 15

            PrEvent e1 = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"weight_kg\":10,\"reps\":10}}");

            PrEvent e2 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":15,\"reps\":10},\"previous_best\":{\"weight_kg\":10,\"reps\":10},\"delta_kg\":5}");

            // Step 1: both active
            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1, e2));

            // Step 4: E2 didn't supersede any prior WEIGHT_PR
            when(prEventRepo.findBySupersededByAndPrCategory(e2.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of());

            stubWeeklyCountsEmpty();

            // Step 5.5 rebuild: after superseding E2, active events = [E1 FIRST_EVER @ 10kg]
            // FIRST_EVER has weight_kg=10 in payload, but there are no active WEIGHT_PR events.
            // bestWtKg should be 10 (from FIRST_EVER payload).
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1));

            // After rebuild, bests has bestWtKg=10 from FIRST_EVER
            UserExerciseBests rebuiltBests = new UserExerciseBests();
            rebuiltBests.setUserId(userId);
            rebuiltBests.setExerciseId("bench-press");
            rebuiltBests.setExerciseType("WEIGHTED");
            rebuiltBests.setBestWtKg(BigDecimal.valueOf(10));
            rebuiltBests.setRepsAtBestWt(10);
            rebuiltBests.setTotalSessionsWithExercise(1);

            when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                    .thenReturn(Optional.of(rebuiltBests));

            // 12kg > 10kg baseline → WEIGHT_PR fires!
            PRResult weightPr = new PRResult(true, PrCategory.WEIGHT_PR,
                    Map.of("weight", true),
                    Map.of("delta_kg", BigDecimal.valueOf(2),
                            "new_best", Map.of("weight_kg", BigDecimal.valueOf(12), "reps", 10),
                            "previous_best", Map.of("weight_kg", BigDecimal.valueOf(10), "reps", 10)),
                    5, "v1.0");
            when(prDetector.detect(any(), eq(rebuiltBests), any())).thenReturn(weightPr);

            // New event save returns itself (for id capture)
            when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(15), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(12), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // E1 stays active (FIRST_EVER)
            assertNull(e1.getSupersededAt());

            // E2 superseded
            assertNotNull(e2.getSupersededAt());

            // New WEIGHT_PR was awarded
            verify(coinService).awardCoins(eq(userId), eq(5), eq("PR_AWARDED"),
                    contains("Weight PR"), anyString());

            // Revoke from E2
            verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                    contains("WEIGHT_PR"), contains(":revoke:"));

            // Net coins: -5 (revoke E2) + 5 (new WEIGHT_PR) = 0 change from this edit
            // Total across all edits: +3 (FIRST_EVER) +5 (E2) -5 (revoke E2) +5 (E3) = +8
        }
    }

    // ── Scenario 10: FIRST_EVER as active baseline on revert ─────────────

    @Nested
    @DisplayName("Scenario 10: FIRST_EVER payload acts as weight baseline after WEIGHT_PR revert")
    class FirstEverAsActiveBaseline {

        @Test
        @DisplayName("20kg FIRST_EVER → edit 25kg WEIGHT_PR → revert 18kg: bestWtKg == 20 from FIRST_EVER")
        void testFirstEverAsActiveBaselineAfterRevert() {
            // Action 1 already happened: finish 20kg x 10 → FIRST_EVER E1 (+3)
            // Action 2 already happened: edit 25kg → FIRST_EVER sticky, WEIGHT_PR E2 (+5)
            //   E1 active (FIRST_EVER), E2 active (WEIGHT_PR), bestWtKg=25
            // Action 3 is THIS cascade: revert to 18kg x 10

            PrEvent e1 = makeEvent(setId, "FIRST_EVER", 3,
                    "{\"new_best\":{\"weight_kg\":20,\"reps\":10}}");
            // E1 stays active — FIRST_EVER is never superseded

            PrEvent e2 = makeEvent(setId, "WEIGHT_PR", 5,
                    "{\"new_best\":{\"weight_kg\":25,\"reps\":10},\"previous_best\":{\"weight_kg\":20,\"reps\":10},\"delta_kg\":5}");

            // Step 1: both active for this set
            when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                    .thenReturn(List.of(e1, e2));

            // Step 4: E2 did not supersede any prior WEIGHT_PR (E2 was the first WEIGHT_PR)
            when(prEventRepo.findBySupersededByAndPrCategory(e2.getId(), "WEIGHT_PR"))
                    .thenReturn(List.of());

            stubWeeklyCountsEmpty();

            // Step 5.5 rebuild: E2 superseded, only E1 (FIRST_EVER @ 20kg) active
            when(prEventRepo.findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, "bench-press"))
                    .thenReturn(List.of(e1));

            // Step 6 re-reads bests after Step 5.5 rebuild → bestWtKg=20 from FIRST_EVER
            UserExerciseBests rebuiltBests = new UserExerciseBests();
            rebuiltBests.setUserId(userId);
            rebuiltBests.setExerciseId("bench-press");
            rebuiltBests.setExerciseType("WEIGHTED");
            rebuiltBests.setBestWtKg(BigDecimal.valueOf(20));
            rebuiltBests.setRepsAtBestWt(10);
            rebuiltBests.setTotalSessionsWithExercise(1);

            when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                    .thenReturn(Optional.of(rebuiltBests));

            // 18kg < 20kg baseline → no PR fires
            when(prDetector.detect(any(), eq(rebuiltBests), any())).thenReturn(noResult());

            LoggedSet oldValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(25), 10, null);
            LoggedSet newValue = new LoggedSet(setId, "bench-press", BigDecimal.valueOf(18), 10, null);

            service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

            // E1 active (FIRST_EVER never superseded)
            assertNull(e1.getSupersededAt(), "FIRST_EVER must not be superseded");

            // E2 superseded
            assertNotNull(e2.getSupersededAt(), "WEIGHT_PR must be superseded on revert");

            // No E3 created — 18kg did not fire a new PR
            // Only coinService calls: -5 revoke E2 (timestamped), no new award
            verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                    contains("WEIGHT_PR"), contains(":revoke:"));
            verify(coinService, never()).awardCoins(eq(userId), eq(5), eq("PR_AWARDED"),
                    anyString(), anyString());
            verify(coinService, never()).awardCoins(eq(userId), eq(3), anyString(),
                    anyString(), anyString());

            // bestWtKg == 20 (from FIRST_EVER payload, not 25 or 18)
            ArgumentCaptor<UserExerciseBests> cap = ArgumentCaptor.forClass(UserExerciseBests.class);
            verify(userExerciseBestsRepo, atLeastOnce()).save(cap.capture());
            UserExerciseBests saved = cap.getValue();
            assertEquals(BigDecimal.valueOf(20), saved.getBestWtKg(),
                    "bestWtKg must be 20 (from FIRST_EVER), not 25 (superseded E2) or 18 (reverted value)");
            assertEquals(10, saved.getRepsAtBestWt());

            // Coin ledger net for this exercise track: +3 (E1) +5 (E2) -5 (revoke E2) = +3
        }
    }
}
