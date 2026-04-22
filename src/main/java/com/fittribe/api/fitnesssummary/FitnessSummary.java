package com.fittribe.api.fitnesssummary;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of a user's pre-computed training history.
 *
 * <p>Stored as JSONB in {@code user_fitness_summary.summary}.
 * Serialised/deserialised by Jackson — no annotations needed on records
 * as Jackson 2.12+ reads record component accessors directly.
 *
 * <p>All nested types are also records so the entire graph is immutable
 * and trivially serialisable.
 */
public record FitnessSummary(

        int version,

        /** Max-weight sets per exercise in the last 4 weeks, one entry per exercise. */
        List<MainLiftEntry> mainLiftStrength,

        /**
         * Total working sets per canonical muscle group in the last 2 weeks.
         * Keys are canonical names: Chest, Back, Shoulders, Legs, Arms, Core.
         */
        Map<String, MuscleVolume> muscleGroupVolume,

        WeeklyConsistency weeklyConsistency,

        RpeTrend rpeTrend,

        PrActivity prActivity,

        /**
         * Days since the user last trained each muscle group (all-time).
         * Keys are canonical names. Muscles never trained are omitted.
         */
        Map<String, Integer> lastTrainedByMuscle

) {

    // ── Nested types ──────────────────────────────────────────────────

    /**
     * Best (heaviest) set for one exercise in the last 4 weeks.
     *
     * @param exerciseId     canonical exercise ID from the catalog
     * @param maxKg          heaviest weight logged (null for bodyweight exercises)
     * @param maxKgReps      reps at that weight
     * @param lastLiftedDate ISO-8601 date string, e.g. "2026-04-20"
     * @param muscleGroup    canonical muscle group, e.g. "Chest"
     */
    public record MainLiftEntry(
            String exerciseId,
            Double maxKg,
            Integer maxKgReps,
            String lastLiftedDate,
            String muscleGroup
    ) {}

    /**
     * Total sets and volume label for one muscle group over the last 2 weeks.
     *
     * @param sets  raw set count
     * @param label "low" | "moderate" | "high"
     */
    public record MuscleVolume(int sets, String label) {

        /** Compute the label from a raw set count. */
        public static MuscleVolume of(int sets) {
            String label = sets < 10 ? "low" : sets <= 20 ? "moderate" : "high";
            return new MuscleVolume(sets, label);
        }
    }

    /**
     * Session frequency signal over the last 2 weeks.
     *
     * @param avgSessionsPerWeek   average completed sessions per week (rounded to 1 dp)
     * @param currentWeekSessions  sessions completed in the current ISO week (Mon–Sun)
     * @param weeklyGoal           user's stated weekly goal
     * @param consistencyLabel     "low" | "fair" | "good" | "high"
     */
    public record WeeklyConsistency(
            double avgSessionsPerWeek,
            int currentWeekSessions,
            int weeklyGoal,
            String consistencyLabel
    ) {

        /** Derive the label from avg sessions and weekly goal. */
        public static String labelFor(double avg, int goal) {
            if (goal <= 0) return "unknown";
            // Round to 3 decimal places to avoid IEEE 754 boundary issues
            // (e.g. 2.4/3 computes to 0.7999... in floating point, not exactly 0.8)
            double ratio = Math.round((avg / goal) * 1000) / 1000.0;
            if (ratio < 0.5)  return "low";
            if (ratio < 0.8)  return "fair";
            if (ratio <= 1.1) return "good";
            return "high";
        }
    }

    /**
     * Perceived-effort trend derived from categorical session feedback ratings.
     *
     * <p>Rating ordering easy → hard: too_easy &lt; good &lt; hard &lt; killed_me.
     * Modal rating is used per window; tie-break favours the harder rating.
     *
     * @param currentWindowLabel  modal rating in the current 4-week window (lowercase)
     * @param previousWindowLabel modal rating in the previous 4-week window (lowercase)
     * @param trendLabel          "climbing" | "flat" | "dropping" | "unknown"
     * @param sampleSize          number of feedback entries used in each window
     */
    public record RpeTrend(
            String currentWindowLabel,
            String previousWindowLabel,
            String trendLabel,
            SampleSize sampleSize
    ) {}

    /** Raw sample counts used for the RPE trend computation. */
    public record SampleSize(int current, int previous) {}

    /**
     * Personal-record activity in the last 4 weeks.
     *
     * @param prCountLast4Weeks  number of non-superseded PR events
     * @param daysSinceLastPr    days since the most recent PR across all time;
     *                           null if the user has never logged a PR
     * @param progressionLabel   "active" | "slow" | "stalled"
     */
    public record PrActivity(
            int prCountLast4Weeks,
            Integer daysSinceLastPr,
            String progressionLabel
    ) {

        /** Derive the label from a 4-week PR count. */
        public static String labelFor(int prCount) {
            if (prCount >= 2) return "active";
            if (prCount == 1) return "slow";
            return "stalled";
        }
    }
}
