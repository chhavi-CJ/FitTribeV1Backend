package com.fittribe.api.findings;

/**
 * The 8 muscle tiles shown on the Weekly Summary report.
 *
 * This is a deliberately smaller taxonomy than the 11-value
 * {@code exercises.muscle_group} column in the DB — several catalog
 * groups (GLUTES, CALVES, FULL_BODY) do not get their own tile and only
 * contribute to report muscle coverage via {@code secondary_muscles}
 * (see {@link MuscleMapper}).
 *
 * Kept intentionally independent from any Workstream B "TrendsMuscle"
 * enum — the Trends tab and the Weekly Report have different
 * taxonomies for legitimate product reasons.
 */
public enum WeeklyReportMuscle {
    CHEST,
    BACK_LATS,
    SHOULDERS,
    BICEPS,
    TRICEPS,
    LEGS_QUADS,
    HAMSTRINGS,
    CORE
}
