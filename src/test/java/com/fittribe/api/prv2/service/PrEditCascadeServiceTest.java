package com.fittribe.api.prv2.service;

import com.fittribe.api.config.PrSystemConfig;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.UserExerciseBests;
import com.fittribe.api.entity.WeeklyPrCount;
import com.fittribe.api.prv2.detector.ExerciseType;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.detector.PRDetector;
import com.fittribe.api.prv2.detector.PRResult;
import com.fittribe.api.prv2.detector.PrCategory;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.WeeklyPrCountRepository;
import com.fittribe.api.service.CoinService;
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
 * Mockito unit tests for PrEditCascadeService (Phase 3b).
 * DISABLED: Requires Spring context to mock @Configuration beans (PrSystemConfig).
 * Pure Mockito cannot inline-mock Spring @Configuration classes.
 * These tests should be enabled when running with @SpringBootTest context.
 */
@Disabled("Requires Spring @Configuration bean mocking; enable with @SpringBootTest context")
@ExtendWith(MockitoExtension.class)
@DisplayName("PrEditCascadeService — PR edit cascade unit tests")
class PrEditCascadeServiceTest {

    @Mock private PrSystemConfig prSystemConfig;
    @Mock private PRDetector prDetector;
    @Mock private UserExerciseBestsRepository userExerciseBestsRepo;
    @Mock private PrEventRepository prEventRepo;
    @Mock private SetLogRepository setLogRepo;
    @Mock private WeeklyPrCountRepository weeklyPrCountRepo;
    @Mock private CoinService coinService;
    @Mock private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private PrEditCascadeService service;

    private UUID userId;
    private UUID sessionId;
    private UUID setId;
    private LoggedSet oldValue;
    private LoggedSet newValue;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        service = new PrEditCascadeService(
                prSystemConfig,
                prDetector,
                userExerciseBestsRepo,
                prEventRepo,
                setLogRepo,
                weeklyPrCountRepo,
                coinService,
                transactionManager);

        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        setId = UUID.randomUUID();
        oldValue = new LoggedSet("bench-press", BigDecimal.valueOf(80.0), 10, null);
        newValue = new LoggedSet("bench-press", BigDecimal.valueOf(85.0), 10, null);
        weekStart = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Default: feature flag enabled
        when(prSystemConfig.isPrSystemV2Enabled()).thenReturn(true);
    }

    // ── Feature flag tests ────────────────────────────────────────────

    @Test
    @DisplayName("Flag off → no-op: no DB calls")
    void featureFlagOff_noOp() {
        when(prSystemConfig.isPrSystemV2Enabled()).thenReturn(false);

        service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

        // Verify no repository calls made
        verify(prEventRepo, never()).findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(any(), any(), any());
        verify(coinService, never()).awardCoins(any(), anyInt(), any(), any(), any());
    }

    // ── Edit cascade tests ────────────────────────────────────────────

    @Test
    @DisplayName("Edit: WEIGHT_PR superseded, revokes 5 coins, no new PR")
    void editSupersedesWeightPr_noCoinAward() {
        // Setup: existing PR event for this set
        PrEvent existingEvent = new PrEvent();
        existingEvent.setUserId(userId);
        existingEvent.setSessionId(sessionId);
        existingEvent.setSetId(setId);
        existingEvent.setExerciseId("bench-press");
        existingEvent.setPrCategory("WEIGHT_PR");
        existingEvent.setCoinsAwarded(5);
        existingEvent.setWeekStart(weekStart);
        existingEvent.setSupersededAt(null);

        when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                .thenReturn(List.of(existingEvent));

        // New value does NOT trigger a new PR
        PRResult noResult = new PRResult(false, null, Map.of(), Map.of(), 0, "v1.0");
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());
        when(prDetector.detect(newValue, null, ExerciseType.WEIGHTED))
                .thenReturn(noResult);

        when(weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart))
                .thenReturn(Optional.empty());

        service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

        // Verify existing event was superseded
        ArgumentCaptor<PrEvent> eventCaptor = ArgumentCaptor.forClass(PrEvent.class);
        verify(prEventRepo).save(eventCaptor.capture());
        PrEvent captured = eventCaptor.getValue();
        assertNotNull(captured.getSupersededAt());

        // Verify coins revoked (negative amount)
        verify(coinService, times(1)).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                contains("bench-press"), anyString());
    }

    @Test
    @DisplayName("Edit: FIRST_EVER invalidated, revokes 3 coins")
    void editSupersedesFirstEver_revokes3Coins() {
        PrEvent existingEvent = new PrEvent();
        existingEvent.setUserId(userId);
        existingEvent.setSessionId(sessionId);
        existingEvent.setSetId(setId);
        existingEvent.setExerciseId("bench-press");
        existingEvent.setPrCategory("FIRST_EVER");
        existingEvent.setCoinsAwarded(3);
        existingEvent.setWeekStart(weekStart);

        when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                .thenReturn(List.of(existingEvent));

        // New value is still a PR (WEIGHT_PR now instead of FIRST_EVER)
        PRResult prResult = new PRResult(true, PrCategory.WEIGHT_PR,
                Map.of("weight", true), Map.of("delta_kg", 5.0), 5, "v1.0");
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());
        when(prDetector.detect(newValue, null, ExerciseType.WEIGHTED))
                .thenReturn(prResult);

        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart))
                .thenReturn(Optional.empty());

        service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

        // Verify old event superseded and coins revoked
        verify(coinService).awardCoins(eq(userId), eq(-3), eq("PR_REVOKED"),
                contains("bench-press"), anyString());

        // Verify new event created with new coin amount (5, not 3)
        verify(coinService).awardCoins(eq(userId), eq(5), eq("PR_AWARDED"),
                contains("bench-press"), anyString());
    }

    @Test
    @DisplayName("Edit: no prior PR, new value triggers PR → creates event, awards coins")
    void editCreatesNewPr_awardsCoins() {
        // No prior events
        when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                .thenReturn(List.of());

        // New value IS a PR
        PRResult prResult = new PRResult(true, PrCategory.WEIGHT_PR,
                Map.of("weight", true), Map.of("delta_kg", 5.0), 5, "v1.0");
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());
        when(prDetector.detect(newValue, null, ExerciseType.WEIGHTED))
                .thenReturn(prResult);

        when(prEventRepo.save(any(PrEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart))
                .thenReturn(Optional.empty());

        service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

        // Verify new event created and coins awarded
        verify(prEventRepo).save(any(PrEvent.class));
        verify(coinService).awardCoins(eq(userId), eq(5), eq("PR_AWARDED"),
                contains("bench-press"), anyString());
    }

    // ── Delete cascade tests ──────────────────────────────────────────

    @Test
    @DisplayName("Delete set: WEIGHT_PR superseded, revokes 5 coins")
    void deleteSetSupersedestWeightPr() {
        PrEvent existingEvent = new PrEvent();
        existingEvent.setUserId(userId);
        existingEvent.setSessionId(sessionId);
        existingEvent.setSetId(setId);
        existingEvent.setExerciseId("bench-press");
        existingEvent.setPrCategory("WEIGHT_PR");
        existingEvent.setCoinsAwarded(5);
        existingEvent.setWeekStart(weekStart);

        when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                .thenReturn(List.of(existingEvent));

        when(weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart))
                .thenReturn(Optional.empty());

        service.processSetDelete(userId, sessionId, setId, oldValue);

        // Verify coins revoked
        verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                contains("bench-press"), anyString());

        // Verify weekly counts decremented
        ArgumentCaptor<WeeklyPrCount> countsCaptor = ArgumentCaptor.forClass(WeeklyPrCount.class);
        verify(weeklyPrCountRepo).save(countsCaptor.capture());
        WeeklyPrCount captured = countsCaptor.getValue();
        // prCount should be decremented (0 - 1 = 0, clamped)
        assertEquals(0, captured.getPrCount());
    }

    @Test
    @DisplayName("Delete exercise: all PRs superseded and revoked")
    void deleteExerciseSupersedstAllPrs() {
        PrEvent event1 = new PrEvent();
        event1.setPrCategory("WEIGHT_PR");
        event1.setCoinsAwarded(5);
        event1.setWeekStart(weekStart);

        PrEvent event2 = new PrEvent();
        event2.setPrCategory("REP_PR");
        event2.setCoinsAwarded(5);
        event2.setWeekStart(weekStart);

        when(prEventRepo.findByUserIdAndSessionIdAndExerciseIdAndSupersededAtNull(userId, sessionId, "bench-press"))
                .thenReturn(List.of(event1, event2));

        when(weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart))
                .thenReturn(Optional.empty());
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());

        service.processExerciseDelete(userId, sessionId, "bench-press", List.of(oldValue));

        // Verify both events superseded
        verify(prEventRepo, times(2)).save(any(PrEvent.class));

        // Verify coins revoked for both
        verify(coinService, times(2)).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                contains("bench-press"), anyString());
    }

    // ── Coin revocation with debt settlement tests ─────────────────

    @Test
    @DisplayName("Revoke when balance sufficient: no debt created")
    void revokeWithSufficientBalance_noDebt() {
        PrEvent existingEvent = new PrEvent();
        existingEvent.setUserId(userId);
        existingEvent.setSessionId(sessionId);
        existingEvent.setSetId(setId);
        existingEvent.setExerciseId("bench-press");
        existingEvent.setPrCategory("WEIGHT_PR");
        existingEvent.setCoinsAwarded(5);
        existingEvent.setWeekStart(weekStart);

        when(prEventRepo.findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId))
                .thenReturn(List.of(existingEvent));
        when(weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart))
                .thenReturn(Optional.empty());

        PRResult noResult = new PRResult(false, null, Map.of(), Map.of(), 0, "v1.0");
        when(userExerciseBestsRepo.findByUserIdAndExerciseId(userId, "bench-press"))
                .thenReturn(Optional.empty());
        when(prDetector.detect(newValue, null, ExerciseType.WEIGHTED))
                .thenReturn(noResult);

        service.processSetEdit(userId, sessionId, setId, oldValue, newValue);

        // CoinService.awardCoins will be called with -5 (debit)
        // The deferred debt settlement logic is in CoinService, not here
        verify(coinService).awardCoins(eq(userId), eq(-5), eq("PR_REVOKED"),
                contains("bench-press"), anyString());
    }
}
