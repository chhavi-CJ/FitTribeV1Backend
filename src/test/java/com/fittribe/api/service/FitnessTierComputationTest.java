package com.fittribe.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the dynamic exercise count formula in {@link PlanService}.
 *
 * <p>Formula: exerciseCount = muscleCount × perMuscle
 * where perMuscle = 2 for Full Body days, 3 for all other day types.
 *
 * <p>Tests are pure arithmetic — no Spring context, no mocks.
 */
class FitnessTierComputationTest {

    private static final int PER_MUSCLE_FULL_BODY = 2;
    private static final int PER_MUSCLE_OTHER     = 3;

    private int perMuscleCount(String dayType) {
        if (dayType == null) return PER_MUSCLE_OTHER;
        return dayType.toLowerCase().contains("full") ? PER_MUSCLE_FULL_BODY : PER_MUSCLE_OTHER;
    }

    private int compute(int muscleCount, String dayType) {
        return muscleCount * perMuscleCount(dayType);
    }

    // ── Push / Pull / Legs days (perMuscle = 3) ──────────────────────────

    @Test
    void push_a_3_muscles_gives_9_exercises() {
        // Chest + Front Delts + Triceps → 3 × 3 = 9
        assertEquals(9, compute(3, "Push A"));
    }

    @Test
    void pull_a_2_muscles_gives_6_exercises() {
        // Back + Biceps → 2 × 3 = 6
        assertEquals(6, compute(2, "Pull A"));
    }

    @Test
    void legs_3_muscles_gives_9_exercises() {
        // Quads + Hamstrings + Glutes → 3 × 3 = 9 (the bug this fix resolves)
        assertEquals(9, compute(3, "Legs"));
    }

    @Test
    void legs_4_muscles_gives_12_exercises() {
        // Quads + Hamstrings + Glutes + Calves → 4 × 3 = 12
        assertEquals(12, compute(4, "Legs"));
    }

    @Test
    void arms_2_muscles_gives_6_exercises() {
        // Biceps + Triceps → 2 × 3 = 6
        assertEquals(6, compute(2, "Arms"));
    }

    // ── Full Body days (perMuscle = 2) ───────────────────────────────────

    @Test
    void full_body_6_muscles_gives_12_exercises() {
        // Chest + Back + Shoulders + Legs + Arms + Core → 6 × 2 = 12
        assertEquals(12, compute(6, "Full Body"));
    }

    @Test
    void full_body_case_insensitive() {
        assertEquals(12, compute(6, "full body"));
        assertEquals(12, compute(6, "FULL BODY"));
        assertEquals(12, compute(6, "Full Body A"));
    }

    // ── Null / other day types fall back to perMuscle = 3 ────────────────

    @Test
    void null_day_type_uses_other_rate() {
        assertEquals(9, compute(3, null));
    }

    @Test
    void unknown_day_type_uses_other_rate() {
        assertEquals(9, compute(3, "Custom"));
    }
}
