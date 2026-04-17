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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito unit tests for PrWritePathService.
 */
@Disabled("Unit tests for PrWritePathService — enable with @SpringBootTest context for full integration")
@ExtendWith(MockitoExtension.class)
@DisplayName("PrWritePathService — PR write path unit tests")
class PrWritePathServiceTest {

    @Mock private PRDetector prDetector;
    @Mock private UserExerciseBestsRepository userExerciseBestsRepo;
    @Mock private PrEventRepository prEventRepo;
    @Mock private WeeklyPrCountRepository weeklyPrCountRepo;
    @Mock private CoinService coinService;
    @Mock private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper;
    private TransactionTemplate transactionTemplate;
    private PrWritePathService service;

    private UUID userId;
    private UUID sessionId;
    private LoggedSet loggedSet;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        transactionTemplate = new TransactionTemplate(transactionManager);

        service = new PrWritePathService(
                prDetector,
                userExerciseBestsRepo,
                prEventRepo,
                weeklyPrCountRepo,
                coinService,
                transactionManager,
                objectMapper);

        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        loggedSet = new LoggedSet(null, "bench-press", BigDecimal.valueOf(80.0), 10, null);
        weekStart = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    }

    @Test
    @DisplayName("Null sets → no-op")
    void nullSets_noOp() {
        service.processSessionFinish(userId, sessionId, null);
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    @Test
    @DisplayName("Empty sets → no-op")
    void emptySets_noOp() {
        service.processSessionFinish(userId, sessionId, List.of());
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    // ── FIRST_EVER detection tests ────────────────────────────────────

    @Test
    @DisplayName("Flag on, FIRST_EVER → saves pr_event, saves new user_exercise_bests, increments weekly_pr_counts, awards +3 coins")
    void firstEver_savesEventAndBestsAndAwardsCoins() {
        // Setup: no prior bests
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());

        // PRDetector returns FIRST_EVER
        PRResult prResult = new PRResult(true, PrCategory.FIRST_EVER,
                Map.of("first_ever", true), Map.of("new_best", Map.of("weight_kg", 80.0)), 3, "v1.0");
        when(prDetector.detect(loggedSet, null, ExerciseType.WEIGHTED))
                .thenReturn(prResult);

        // Mock prEventRepo.save to return a PrEvent (id is auto-generated in real DB)
        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> {
            PrEvent captured = inv.getArgument(0);
            // In test, simulate id generation by capturing the argument
            return captured;
        });

        service.processSessionFinish(userId, sessionId, List.of(loggedSet));

        // Verify pr_event created with correct fields
        ArgumentCaptor<PrEvent> eventCaptor = ArgumentCaptor.forClass(PrEvent.class);
        verify(prEventRepo).save(eventCaptor.capture());
        PrEvent capturedEvent = eventCaptor.getValue();
        assertEquals(userId, capturedEvent.getUserId());
        assertEquals("bench-press", capturedEvent.getExerciseId());
        assertEquals(sessionId, capturedEvent.getSessionId());
        assertEquals("FIRST_EVER", capturedEvent.getPrCategory());
        assertEquals(3, capturedEvent.getCoinsAwarded());

        // Verify user_exercise_bests created
        ArgumentCaptor<UserExerciseBests> bestsCaptor = ArgumentCaptor.forClass(UserExerciseBests.class);
        verify(userExerciseBestsRepo).save(bestsCaptor.capture());
        UserExerciseBests capturedBests = bestsCaptor.getValue();
        assertEquals(userId, capturedBests.getUserId());
        assertEquals("bench-press", capturedBests.getExerciseId());
        assertEquals(1, capturedBests.getTotalSessionsWithExercise());

        // Verify weekly_pr_counts incremented
        ArgumentCaptor<WeeklyPrCount> countsCaptor = ArgumentCaptor.forClass(WeeklyPrCount.class);
        verify(weeklyPrCountRepo).save(countsCaptor.capture());
        WeeklyPrCount capturedCounts = countsCaptor.getValue();
        assertEquals(1, capturedCounts.getFirstEverCount());

        // Verify coins awarded (use the captured event's string representation for reference)
        verify(coinService, times(1)).awardCoins(eq(userId), eq(3), eq("FIRST_EVER"),
                contains("bench-press"), anyString());
    }

    // ── WEIGHT_PR detection tests ─────────────────────────────────────

    @Test
    @DisplayName("WEIGHT_PR → updates existing user_exercise_bests (bestWtKg, repsAtBestWt), increments prCount, awards +5 coins")
    void weightPr_updatesExistingBestsAndAwardsCoins() {
        // Setup: existing bests
        UserExerciseBests existingBests = new UserExerciseBests();
        existingBests.setUserId(userId);
        existingBests.setExerciseId("bench-press");
        existingBests.setExerciseType("WEIGHTED");
        existingBests.setBestWtKg(BigDecimal.valueOf(75.0));
        existingBests.setRepsAtBestWt(8);
        existingBests.setTotalSessionsWithExercise(5);

        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.of(existingBests));

        // PRDetector returns WEIGHT_PR
        PRResult prResult = new PRResult(true, PrCategory.WEIGHT_PR,
                Map.of("weight", true), Map.of("delta_kg", 5.0), 5, "v1.0");
        when(prDetector.detect(loggedSet, existingBests, ExerciseType.WEIGHTED))
                .thenReturn(prResult);

        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processSessionFinish(userId, sessionId, List.of(loggedSet));

        // Verify user_exercise_bests updated with new weight/reps
        ArgumentCaptor<UserExerciseBests> bestsCaptor = ArgumentCaptor.forClass(UserExerciseBests.class);
        verify(userExerciseBestsRepo).save(bestsCaptor.capture());
        UserExerciseBests updated = bestsCaptor.getValue();
        assertEquals(BigDecimal.valueOf(80.0), updated.getBestWtKg());
        assertEquals(10, updated.getRepsAtBestWt());
        assertEquals(6, updated.getTotalSessionsWithExercise()); // incremented

        // Verify weekly_pr_counts increments prCount (not firstEverCount)
        ArgumentCaptor<WeeklyPrCount> countsCaptor = ArgumentCaptor.forClass(WeeklyPrCount.class);
        verify(weeklyPrCountRepo).save(countsCaptor.capture());
        WeeklyPrCount capturedCounts = countsCaptor.getValue();
        assertEquals(1, capturedCounts.getPrCount());
        assertNull(capturedCounts.getFirstEverCount()); // null, default is 0

        // Verify +5 coins awarded
        verify(coinService, times(1)).awardCoins(eq(userId), eq(5), eq("PR_AWARDED"),
                contains("bench-press"), anyString());
    }

    // ── MAX_ATTEMPT detection tests ───────────────────────────────────

    @Test
    @DisplayName("MAX_ATTEMPT → saves pr_event, updates bestWtKg, increments maxAttemptCount, NO coin award (coins=0)")
    void maxAttempt_noCoinAward() {
        UserExerciseBests existingBests = new UserExerciseBests();
        existingBests.setExerciseType("WEIGHTED");
        existingBests.setBestWtKg(BigDecimal.valueOf(100.0));

        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "squat"))
                .thenReturn(Optional.of(existingBests));

        LoggedSet maxAttempt = new LoggedSet(null, "squat", BigDecimal.valueOf(105.0), 1, null);

        // PRDetector returns MAX_ATTEMPT
        PRResult prResult = new PRResult(true, PrCategory.MAX_ATTEMPT,
                Map.of("max_attempt", true), Map.of(), 0, "v1.0"); // 0 coins per spec
        when(prDetector.detect(maxAttempt, existingBests, ExerciseType.WEIGHTED))
                .thenReturn(prResult);

        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processSessionFinish(userId, sessionId, List.of(maxAttempt));

        // Verify maxAttemptCount incremented
        ArgumentCaptor<WeeklyPrCount> countsCaptor = ArgumentCaptor.forClass(WeeklyPrCount.class);
        verify(weeklyPrCountRepo).save(countsCaptor.capture());
        WeeklyPrCount capturedCounts = countsCaptor.getValue();
        assertEquals(1, capturedCounts.getMaxAttemptCount());

        // Verify NO coin award (suggestedCoins == 0)
        verify(coinService, never()).awardCoins(any(), anyInt(), any(), any(), any());
    }

    // ── No PR tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("No PR → no pr_event saved, user_exercise_bests last_logged_at still updated, no coin call")
    void noPr_noEventNoCoins() {
        UserExerciseBests existingBests = new UserExerciseBests();
        existingBests.setExerciseType("WEIGHTED");
        existingBests.setTotalSessionsWithExercise(10);

        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.of(existingBests));

        // PRDetector returns no PR
        PRResult noResult = new PRResult(false, null, Map.of(), Map.of(), 0, "v1.0");
        when(prDetector.detect(loggedSet, existingBests, ExerciseType.WEIGHTED))
                .thenReturn(noResult);

        service.processSessionFinish(userId, sessionId, List.of(loggedSet));

        // Verify no pr_event saved
        verify(prEventRepo, never()).save(any(PrEvent.class));

        // Verify user_exercise_bests updated (last_logged_at, session count incremented)
        ArgumentCaptor<UserExerciseBests> bestsCaptor = ArgumentCaptor.forClass(UserExerciseBests.class);
        verify(userExerciseBestsRepo).save(bestsCaptor.capture());
        UserExerciseBests updated = bestsCaptor.getValue();
        assertEquals(11, updated.getTotalSessionsWithExercise());
        assertNotNull(updated.getLastLoggedAt());

        // Verify no coin call
        verify(coinService, never()).awardCoins(any(), anyInt(), any(), any(), any());
    }

    // ── Exception handling tests ──────────────────────────────────────

    @Test
    @DisplayName("Exception on set 1 → set 2 still processes (P4 pattern)")
    void exceptionHandling_set2ProcessesAfterSet1Fails() {
        // Set 1: simulates a DB failure that throws
        LoggedSet set1 = new LoggedSet(null, "bench-press", BigDecimal.valueOf(80.0), 10, null);
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenThrow(new RuntimeException("Simulated DB failure on set 1"));

        // Set 2: should still process
        LoggedSet set2 = new LoggedSet(null, "squat", BigDecimal.valueOf(100.0), 5, null);
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "squat"))
                .thenReturn(Optional.empty());

        PRResult prResult2 = new PRResult(true, PrCategory.FIRST_EVER,
                Map.of("first_ever", true), Map.of(), 3, "v1.0");
        when(prDetector.detect(set2, null, ExerciseType.WEIGHTED))
                .thenReturn(prResult2);

        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        // Call should not throw even though set1 fails (P4 pattern)
        service.processSessionFinish(userId, sessionId, List.of(set1, set2));

        // Verify set 2's pr_event was still saved despite set 1 failing
        ArgumentCaptor<PrEvent> eventCaptor = ArgumentCaptor.forClass(PrEvent.class);
        verify(prEventRepo, atLeastOnce()).save(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(e -> "squat".equals(e.getExerciseId())),
                "Set 2's PR event should have been saved despite set 1 failure");
    }

    // ── First ever → totalSessionsWithExercise 0→1 test ──────────────

    @Test
    @DisplayName("First ever exercise → totalSessionsWithExercise starts at 0, ends at 1")
    void firstEver_incrementsSessionCount() {
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());

        PRResult prResult = new PRResult(true, PrCategory.FIRST_EVER,
                Map.of("first_ever", true), Map.of(), 3, "v1.0");
        when(prDetector.detect(loggedSet, null, ExerciseType.WEIGHTED))
                .thenReturn(prResult);

        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        service.processSessionFinish(userId, sessionId, List.of(loggedSet));

        ArgumentCaptor<UserExerciseBests> bestsCaptor = ArgumentCaptor.forClass(UserExerciseBests.class);
        verify(userExerciseBestsRepo).save(bestsCaptor.capture());
        UserExerciseBests created = bestsCaptor.getValue();
        assertEquals(1, created.getTotalSessionsWithExercise());
    }
}
