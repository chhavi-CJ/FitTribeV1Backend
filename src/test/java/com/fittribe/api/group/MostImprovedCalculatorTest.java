package com.fittribe.api.group;

import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import com.fittribe.api.service.MostImprovedCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MostImprovedCalculatorTest {

    private UserWeeklyStatsRepository statsRepo;
    private MostImprovedCalculator    calculator;

    private static final UUID      USER_ID   = UUID.randomUUID();
    private static final LocalDate WEEK      = LocalDate.of(2026, 4, 20); // Monday

    @BeforeEach
    void setUp() {
        statsRepo  = mock(UserWeeklyStatsRepository.class);
        calculator = new MostImprovedCalculator(statsRepo);
        // default: no previous weeks
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK)).thenReturn(List.of());
    }

    // ── NOT_ELIGIBLE ─────────────────────────────────────────────────────────

    @Test
    void no_stats_row_returns_not_eligible() {
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK)).thenReturn(Optional.empty());

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.NOT_ELIGIBLE, r.status);
        assertNull(r.displayPct);
        assertNull(r.internalPct);
    }

    @Test
    void zero_sessions_returns_not_eligible() {
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(0, "1000", true)));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.NOT_ELIGIBLE, r.status);
    }

    // ── GOAL_NOT_HIT ─────────────────────────────────────────────────────────

    @Test
    void goal_not_hit_returns_goal_not_hit() {
        // sessions > 0 but user did not hit weekly goal
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(3, "1500", false)));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.GOAL_NOT_HIT, r.status);
        assertNull(r.displayPct);
    }

    // ── ROUTE_TO_COMEBACK ────────────────────────────────────────────────────

    @Test
    void all_four_previous_weeks_zero_sessions_returns_route_to_comeback() {
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(4, "2000", true)));
        // 4 previous weeks all with 0 sessions (job ran but user was absent)
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK))
                .thenReturn(List.of(prevWeek(0, "0"), prevWeek(0, "0"),
                                    prevWeek(0, "0"), prevWeek(0, "0")));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.ROUTE_TO_COMEBACK, r.status);
    }

    // ── NEW_MEMBER ───────────────────────────────────────────────────────────

    @Test
    void one_valid_previous_week_returns_new_member() {
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(4, "1800", true)));
        // Only 1 non-zero previous week — not enough for a baseline
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK))
                .thenReturn(List.of(prevWeek(3, "1500"), prevWeek(0, "0"), prevWeek(0, "0")));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.NEW_MEMBER, r.status);
        assertNull(r.displayPct);
    }

    // ── RANKED ───────────────────────────────────────────────────────────────

    @Test
    void two_valid_previous_weeks_computes_baseline_and_ranks() {
        // baseline = (1000 + 1500) / 2 = 1250; thisWeek = 2500
        // ratio = 2500 / 1250 = 2.0 → internalPct = 100
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(4, "2500", true)));
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK))
                .thenReturn(List.of(prevWeek(3, "1000"), prevWeek(2, "1500")));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.RANKED, r.status);
        assertEquals(100, r.displayPct);
        assertEquals(100, r.internalPct);
        assertFalse(r.isOverCap);
    }

    @Test
    void negative_improvement_floors_display_pct_to_zero() {
        // baseline = (1000 + 1500) / 2 = 1250; thisWeek = 800
        // ratio = 0.64 → internalPct = -36; displayPct floored to 0
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(4, "800", true)));
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK))
                .thenReturn(List.of(prevWeek(3, "1000"), prevWeek(2, "1500")));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.RANKED, r.status);
        assertEquals(0, r.displayPct, "display must be floored to 0");
        assertTrue(r.internalPct < 0, "internalPct preserves raw negative value");
        assertFalse(r.isOverCap);
    }

    @Test
    void over_200_improvement_caps_display_at_200_and_sets_over_cap() {
        // baseline = (1000 + 500) / 2 = 750; thisWeek = 5000
        // ratio ≈ 6.6667 → internalPct ≈ 566; displayPct = 200, isOverCap = true
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(5, "5000", true)));
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK))
                .thenReturn(List.of(prevWeek(3, "1000"), prevWeek(2, "500")));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.RANKED, r.status);
        assertEquals(200, r.displayPct, "display must be capped at 200");
        assertTrue(r.internalPct > 200, "internalPct preserves raw value beyond cap");
        assertTrue(r.isOverCap);
    }

    @Test
    void zero_session_weeks_excluded_from_baseline_average() {
        // history: 3000, 0, 2000, 0 → valid=[3000,2000], baseline=2500
        // thisWeek = 3750 → ratio = 1.5 → internalPct = 50
        when(statsRepo.findByUserIdAndWeekStartDate(USER_ID, WEEK))
                .thenReturn(Optional.of(thisWeek(4, "3750", true)));
        when(statsRepo.findLast4WeeksBefore(USER_ID, WEEK))
                .thenReturn(List.of(
                        prevWeek(3, "3000"),
                        prevWeek(0, "0"),
                        prevWeek(2, "2000"),
                        prevWeek(0, "0")));

        MostImprovedCalculator.MostImprovedResult r = calculator.calculate(USER_ID, WEEK);

        assertEquals(MostImprovedCalculator.MostImprovedStatus.RANKED, r.status);
        assertEquals(50, r.internalPct);
        assertEquals(50, r.displayPct);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static UserWeeklyStats thisWeek(int sessions, String volume, boolean goalHit) {
        UserWeeklyStats s = new UserWeeklyStats();
        s.setUserId(USER_ID);
        s.setWeekStartDate(WEEK);
        s.setSessionsCount(sessions);
        s.setTotalVolumeKg(new BigDecimal(volume));
        s.setWeeklyGoalTarget(4);
        s.setWeeklyGoalHit(goalHit);
        return s;
    }

    private static UserWeeklyStats prevWeek(int sessions, String volume) {
        UserWeeklyStats s = new UserWeeklyStats();
        s.setUserId(USER_ID);
        s.setSessionsCount(sessions);
        s.setTotalVolumeKg(new BigDecimal(volume));
        return s;
    }
}
