package com.fittribe.api.strengthscore;

/**
 * The 6-muscle taxonomy used by the Trends tab strength score system.
 *
 * <p>This is distinct from the 8-muscle {@code WeeklyReportMuscle} taxonomy
 * used in weekly reports. The Trends system groups LEGS, HAMSTRINGS, GLUTES,
 * and CALVES into a single LEGS bucket for simplicity — end users care more
 * about "leg strength progress" than breaking it into quads vs. hamstrings.
 *
 * <p>CORE and FULL_BODY exercises do not map to any TrendsMuscle and are
 * skipped from strength scoring (they're not amenable to 1RM standardisation).
 */
public enum TrendsMuscle {
    CHEST,
    BACK,
    LEGS,
    SHOULDERS,
    BICEPS,
    TRICEPS;
}
