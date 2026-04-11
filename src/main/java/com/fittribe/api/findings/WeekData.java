package com.fittribe.api.findings;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Frozen weekly snapshot consumed by {@code FindingsRule} implementations
 * (Wynners A2.1) and the downstream {@code FindingsGenerator} (A2.2).
 *
 * Built by {@link WeekDataBuilder}. Rules must not reach back into the
 * database — everything they need is already here. That restriction is
 * what makes the rules unit-testable in isolation against fixture
 * WeekData objects.
 *
 * Immutability matters: rules run in sequence but none of them should
 * mutate shared state, so this is a record of records. If a rule
 * accidentally tries to {@code add} to one of the maps or lists, it
 * will either mutate the Builder's working copy (bad) or throw on an
 * unmodifiable view — either way, we want to catch it early. All
 * collections in this record MUST be wrapped in {@link java.util.Collections}
 * unmodifiable views by the builder before being passed in.
 */
public record WeekData(

        // ── Identity / window ──────────────────────────────────────────
        UUID userId,
        LocalDate weekStart,          // inclusive, UTC Monday
        LocalDate weekEnd,            // exclusive, the following Monday
        int weekNumber,               // from workout_sessions.week_number; 1 if absent
        boolean isWeekOne,            // convenience: weekNumber == 1
        String userFirstName,         // from users.display_name, first token

        // ── Headline counts ────────────────────────────────────────────
        int sessionsLogged,           // COMPLETED sessions in [weekStart, weekEnd)
        int sessionsGoal,             // users.weekly_goal, default 4
        boolean weeklyGoalHit,        // sessionsLogged >= sessionsGoal
        BigDecimal totalKgVolume,     // sum of workout_sessions.total_volume_kg
        int prCount,                  // distinct exerciseIds with isPr=true this week

        // ── Per-session summaries (ordered by finished_at asc) ─────────
        List<SessionSummary> sessions,

        // ── Indexed views for rules ────────────────────────────────────
        /** exerciseId → all sets logged this week across all sessions */
        Map<String, List<LoggedSet>> setsByExercise,
        /** tile → # of distinct sessions that trained it (after smart filter) */
        Map<WeeklyReportMuscle, Integer> sessionsByMuscle,

        // ── Push / pull / legs session counts for PushPullImbalanceRule ─
        int pushSessionCount,
        int pullSessionCount,
        int legsSessionCount,

        // ── Top sets for PR regression rule ────────────────────────────
        /** exerciseId → this week's top set (heaviest weight, tiebreak reps) */
        Map<String, TopSet> thisWeekTopSets,
        /** exerciseId → previous week's top set (from set_logs) */
        Map<String, TopSet> previousWeekTopSets,

        // ── PR entries for MultiPrWeek / FullConsistency rules ─────────
        /** One entry per exercise that set a new PR this week */
        List<PrEntry> personalRecords,

        // ── Plan-vs-actual for SingleTooLight / MultipleTooLight ───────
        /** exerciseId → (target from ai_planned_weights, logged max from exercises JSONB) */
        Map<String, TargetVsLogged> targetExercises,

        // ── Catalog snapshot for rules that need muscle group lookups ──
        /** exerciseId → catalog metadata (muscle_group, secondaries, bodyweight, push/pull) */
        Map<String, ExerciseMeta> exerciseCatalog

) {

    /** Per-session summary. Ordered by finished_at asc in the parent list. */
    public record SessionSummary(
            UUID sessionId,
            Instant finishedAt,
            BigDecimal totalVolumeKg,
            /** Tiles trained by this session after smart filter. */
            Set<WeeklyReportMuscle> musclesTrained,
            boolean isPush,
            boolean isPull,
            boolean isLegs
    ) {}

    /** One logged set, as extracted from the session's exercises JSONB. */
    public record LoggedSet(
            String exerciseId,
            BigDecimal weightKg,
            int reps,
            int setNumber,
            boolean isPr
    ) {}

    /** Top set for an exercise in a window. Heaviest weight; tiebreak = most reps. */
    public record TopSet(BigDecimal weightKg, int reps) {}

    /**
     * A PR set in this week, with the previous all-time best for comparison.
     * {@code previousMaxKg} is null if this is the first ever log for the
     * exercise (so the current week is also the first recorded max).
     */
    public record PrEntry(
            String exerciseId,
            BigDecimal newMaxKg,
            BigDecimal previousMaxKg,
            int reps
    ) {}

    /**
     * Plan target vs actual logged max for one exercise this week.
     * Target is sourced from {@code workout_sessions.ai_planned_weights}
     * (the AI plan snapshot taken at session start), NOT from
     * {@code user_plans.days} — that's a deliberate override of v1.1.
     */
    public record TargetVsLogged(
            String exerciseId,
            BigDecimal targetKg,
            BigDecimal loggedMaxKg
    ) {}

    /**
     * Catalog snapshot for one exercise referenced this week. Carries
     * the push/pull/legs classification derived from {@code muscle_group},
     * so rules don't need to re-derive it.
     */
    public record ExerciseMeta(
            String id,
            String name,
            String muscleGroup,
            String[] secondaryMuscles,
            boolean isBodyweight,
            PushPull classification
    ) {
        public enum PushPull { PUSH, PULL, LEGS, OTHER }
    }
}
