package com.fittribe.api.group;

import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import com.fittribe.api.service.EffortScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EffortScoreCalculatorTest {

    private UserWeeklyStatsRepository statsRepo;
    private EffortScoreCalculator calculator;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final LocalDate WEEK = LocalDate.of(2026, 4, 20); // Monday

    @BeforeEach
    void setUp() {
        statsRepo  = mock(UserWeeklyStatsRepository.class);
        calculator = new EffortScoreCalculator(statsRepo);
    }

    @Test
    void returns_zero_when_no_stats_row() {
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.empty());
        assertEquals(0, calculator.calculateEffortScore(USER_ID, WEEK));
    }

    @Test
    void returns_zero_when_sessions_count_is_zero() {
        UserWeeklyStats stats = statsWithSessions(0, 4);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));
        assertEquals(0, calculator.calculateEffortScore(USER_ID, WEEK));
    }

    @Test
    void new_user_no_baseline_gets_neutral_intensity_15() {
        // goal=4, sessions=4 → baseSessions=32, stretch=0 → sessionsPoints=32
        // baseline=null → intensityPoints=15
        // prs=0 → progressionPoints=0
        // 3plusSets=0, 45min=0 → hardworkPoints=0
        // total = 32 + 15 + 0 + 0 = 47
        UserWeeklyStats stats = statsWithSessions(4, 4);
        stats.setPrsHit(0);
        stats.setSessionsWith3PlusSets(0);
        stats.setSessions45MinPlus(0);
        stats.setBaselineVolumeKg(null);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        assertEquals(47, calculator.calculateEffortScore(USER_ID, WEEK));
    }

    @Test
    void sessions_points_cap_at_40() {
        // goal=4, sessions=8 → base=32, stretch=min(2,4)*4=8 → total=40 (capped)
        UserWeeklyStats stats = statsWithSessions(8, 4);
        stats.setBaselineVolumeKg(new BigDecimal("1000"));
        stats.setTotalVolumeKg(new BigDecimal("1000")); // ratio=1.0 → intensity=20
        stats.setPrsHit(0);
        stats.setSessionsWith3PlusSets(0);
        stats.setSessions45MinPlus(0);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        int score = calculator.calculateEffortScore(USER_ID, WEEK);
        // sessions=40 (capped), intensity=20, progression=0, hardwork=0 → 60
        assertEquals(60, score);
    }

    @Test
    void intensity_points_cap_at_30() {
        // ratio = 3.0 → raw=60 → capped at 30
        UserWeeklyStats stats = statsWithSessions(4, 4);
        stats.setBaselineVolumeKg(new BigDecimal("500"));
        stats.setTotalVolumeKg(new BigDecimal("1500")); // ratio=3.0 → 3*20=60 → capped 30
        stats.setPrsHit(0);
        stats.setSessionsWith3PlusSets(0);
        stats.setSessions45MinPlus(0);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        int score = calculator.calculateEffortScore(USER_ID, WEEK);
        // sessions=32, intensity=30 (capped), progression=0, hardwork=0 → 62
        assertEquals(62, score);
    }

    @Test
    void progression_points_cap_at_20_after_4_prs() {
        // 5 PRs → 25 raw → capped at 20
        UserWeeklyStats stats = statsWithSessions(4, 4);
        stats.setBaselineVolumeKg(null); // neutral 15
        stats.setPrsHit(5);
        stats.setSessionsWith3PlusSets(0);
        stats.setSessions45MinPlus(0);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        int score = calculator.calculateEffortScore(USER_ID, WEEK);
        // sessions=32, intensity=15, progression=20 (capped), hardwork=0 → 67
        assertEquals(67, score);
    }

    @Test
    void hardwork_points_cap_at_10_after_5_qualifying_sessions() {
        // both 3plus and 45min = 6 → qualifying=6, raw=12 → capped at 10
        UserWeeklyStats stats = statsWithSessions(4, 4);
        stats.setBaselineVolumeKg(null); // neutral 15
        stats.setPrsHit(0);
        stats.setSessionsWith3PlusSets(6);
        stats.setSessions45MinPlus(6);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        int score = calculator.calculateEffortScore(USER_ID, WEEK);
        // sessions=32, intensity=15, progression=0, hardwork=10 (capped) → 57
        assertEquals(57, score);
    }

    @Test
    void hardwork_uses_lower_of_3plussets_and_45min() {
        // 3plusSets=3, 45min=1 → qualifying=1, hardwork=2
        UserWeeklyStats stats = statsWithSessions(4, 4);
        stats.setBaselineVolumeKg(null);
        stats.setPrsHit(0);
        stats.setSessionsWith3PlusSets(3);
        stats.setSessions45MinPlus(1);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        int score = calculator.calculateEffortScore(USER_ID, WEEK);
        // sessions=32, intensity=15, progression=0, hardwork=2 → 49
        assertEquals(49, score);
    }

    @Test
    void perfect_score_is_100() {
        // goal=5, sessions=7 → base=40, stretch=8 → capped 40
        // volume/baseline = 1.5 → 30 (capped)
        // prs=4 → 20 (max)
        // qualifying=5 → 10 (capped)
        // total = 40+30+20+10 = 100
        UserWeeklyStats stats = statsWithSessions(7, 5);
        stats.setBaselineVolumeKg(new BigDecimal("1000"));
        stats.setTotalVolumeKg(new BigDecimal("1500")); // ratio=1.5 → 30
        stats.setPrsHit(4);
        stats.setSessionsWith3PlusSets(5);
        stats.setSessions45MinPlus(5);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.of(stats));

        assertEquals(100, calculator.calculateEffortScore(USER_ID, WEEK));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static UserWeeklyStats statsWithSessions(int sessions, int goal) {
        UserWeeklyStats s = new UserWeeklyStats();
        s.setUserId(USER_ID);
        s.setWeekStartDate(WEEK);
        s.setSessionsCount(sessions);
        s.setWeeklyGoalTarget(goal);
        s.setTotalVolumeKg(new BigDecimal("1000"));
        s.setWeeklyGoalHit(sessions >= goal);
        return s;
    }
}
