package com.fittribe.api.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the dynamic exercise floor/ceiling formula in {@link PlanService}.
 *
 * <p>Formula:
 * <pre>
 *   floor   = max(muscleCount * PER_MUSCLE_MIN, tierMin)
 *   ceiling = min(muscleCount * PER_MUSCLE_MAX, tierHardCap)
 *   if floor > ceiling: ceiling = floor  (warn and clamp)
 * </pre>
 *
 * <p>Tests are pure arithmetic — no Spring context, no mocks.
 */
class FitnessTierComputationTest {

    // Constants mirrored from PlanService (package-private via same package)
    private static final Map<String, Integer> TIER_MIN     = Map.of("BEGINNER", 4, "INTERMEDIATE", 5, "ADVANCED", 6);
    private static final Map<String, Integer> TIER_HARD_CAP = Map.of("BEGINNER", 6, "INTERMEDIATE", 8, "ADVANCED", 10);
    private static final int PER_MUSCLE_MIN = 2;
    private static final int PER_MUSCLE_MAX = 3;

    /** Replicates the exact formula from generateTodaysPlan(). */
    private int[] compute(int muscleCount, String level) {
        String levelKey  = level == null ? "INTERMEDIATE" : level.toUpperCase();
        int tierMin      = TIER_MIN.getOrDefault(levelKey, 5);
        int tierHardCap  = TIER_HARD_CAP.getOrDefault(levelKey, 8);
        int exerciseMin  = Math.max(muscleCount * PER_MUSCLE_MIN, tierMin);
        int exerciseMax  = Math.min(muscleCount * PER_MUSCLE_MAX, tierHardCap);
        if (exerciseMin > exerciseMax) {
            exerciseMax = exerciseMin;
        }
        return new int[]{exerciseMin, exerciseMax};
    }

    // ── Standard cases ────────────────────────────────────────────────

    @Test
    void push_a_beginner_3_muscles_floor_equals_ceiling() {
        // 3×2=6 > tierMin(4); 3×3=9 > tierCap(6) → min=6, max=6
        int[] r = compute(3, "BEGINNER");
        assertEquals(6, r[0], "exerciseMin");
        assertEquals(6, r[1], "exerciseMax");
    }

    @Test
    void push_a_intermediate_3_muscles() {
        // 3×2=6 > tierMin(5); 3×3=9 > tierCap(8) → min=6, max=8
        int[] r = compute(3, "INTERMEDIATE");
        assertEquals(6, r[0], "exerciseMin");
        assertEquals(8, r[1], "exerciseMax");
    }

    @Test
    void push_a_advanced_3_muscles() {
        // 3×2=6 = tierMin(6); 3×3=9 < tierCap(10) → min=6, max=9
        int[] r = compute(3, "ADVANCED");
        assertEquals(6, r[0], "exerciseMin");
        assertEquals(9, r[1], "exerciseMax");
    }

    @Test
    void pull_a_beginner_2_muscles() {
        // 2×2=4 = tierMin(4); 2×3=6 = tierCap(6) → min=4, max=6
        int[] r = compute(2, "BEGINNER");
        assertEquals(4, r[0], "exerciseMin");
        assertEquals(6, r[1], "exerciseMax");
    }

    @Test
    void legs_advanced_2_muscles() {
        // 2×2=4 < tierMin(6) → min=6; 2×3=6 < tierCap(10) → max=6
        int[] r = compute(2, "ADVANCED");
        assertEquals(6, r[0], "exerciseMin");
        assertEquals(6, r[1], "exerciseMax");
    }

    // ── Floor > ceiling cases (WARN fires, ceiling clamped to floor) ──

    @Test
    void full_body_beginner_4_muscles_floor_exceeds_ceiling() {
        // 4×2=8 > tierMin(4) → floor=8; 4×3=12 > tierCap(6) → ceiling=6
        // floor(8) > ceiling(6) → clamp: ceiling=8
        int[] r = compute(4, "BEGINNER");
        assertEquals(8, r[0], "exerciseMin");
        assertEquals(8, r[1], "exerciseMax — clamped to floor");
    }

    @Test
    void full_body_beginner_6_muscles_extreme_case() {
        // 6×2=12 > tierMin(4) → floor=12; 6×3=18 > tierCap(6) → ceiling=6
        // floor(12) > ceiling(6) → clamp: ceiling=12
        int[] r = compute(6, "BEGINNER");
        assertEquals(12, r[0], "exerciseMin");
        assertEquals(12, r[1], "exerciseMax — clamped to floor");
    }

    @Test
    void full_body_advanced_6_muscles_floor_exceeds_hard_cap() {
        // 6×2=12 > tierMin(6) → floor=12; 6×3=18 > tierCap(10) → ceiling=10
        // floor(12) > ceiling(10) → clamp: ceiling=12
        int[] r = compute(6, "ADVANCED");
        assertEquals(12, r[0], "exerciseMin");
        assertEquals(12, r[1], "exerciseMax — clamped to floor");
    }

    // ── Null / unknown fitness level fallback ─────────────────────────

    @Test
    void null_fitness_level_treated_as_intermediate() {
        // same as INTERMEDIATE with 3 muscles
        int[] r = compute(3, null);
        assertEquals(6, r[0], "exerciseMin");
        assertEquals(8, r[1], "exerciseMax");
    }

    @Test
    void unknown_fitness_level_treated_as_intermediate_via_getOrDefault() {
        // "EXPERT" not in map → getOrDefault → 5 min, 8 cap
        // 3×2=6 > 5 → floor=6; 3×3=9 > 8 → ceiling=8
        int[] r = compute(3, "EXPERT");
        assertEquals(6, r[0], "exerciseMin");
        assertEquals(8, r[1], "exerciseMax");
    }
}
