package com.fittribe.api;

import com.fittribe.api.util.MuscleGroupUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MuscleGroupUtil#canonicalize(String)}.
 *
 * <p>Every case the old private {@code PlanService.canonicalizeMuscle()} handled
 * must pass here — plus the edge cases introduced by extraction into a shared util.
 */
class MuscleGroupUtilTest {

    // ── Null / blank / unknown ────────────────────────────────────────

    @Test
    void null_input_returns_empty() {
        assertEquals("", MuscleGroupUtil.canonicalize(null));
    }

    @Test
    void empty_string_returns_empty() {
        assertEquals("", MuscleGroupUtil.canonicalize(""));
    }

    @Test
    void whitespace_only_returns_empty() {
        assertEquals("", MuscleGroupUtil.canonicalize("   "));
    }

    @Test
    void unknown_value_returns_empty() {
        assertEquals("", MuscleGroupUtil.canonicalize("random garbage"));
    }

    @Test
    void numeric_string_returns_empty() {
        assertEquals("", MuscleGroupUtil.canonicalize("42"));
    }

    // ── Chest ─────────────────────────────────────────────────────────

    @Test
    void chest_lowercase() {
        assertEquals("Chest", MuscleGroupUtil.canonicalize("chest"));
    }

    @Test
    void chest_exact_match_is_idempotent() {
        assertEquals("Chest", MuscleGroupUtil.canonicalize("Chest"));
    }

    @Test
    void upper_chest() {
        assertEquals("Chest", MuscleGroupUtil.canonicalize("Upper Chest"));
    }

    @Test
    void lower_chest() {
        assertEquals("Chest", MuscleGroupUtil.canonicalize("Lower Chest"));
    }

    @Test
    void chest_mixed_case() {
        assertEquals("Chest", MuscleGroupUtil.canonicalize("CHEST"));
    }

    // ── Back ──────────────────────────────────────────────────────────

    @Test
    void back_exact() {
        assertEquals("Back", MuscleGroupUtil.canonicalize("Back"));
    }

    @Test
    void upper_back() {
        assertEquals("Back", MuscleGroupUtil.canonicalize("Upper Back"));
    }

    @Test
    void back_width() {
        assertEquals("Back", MuscleGroupUtil.canonicalize("Back Width"));
    }

    @Test
    void back_lowercase() {
        assertEquals("Back", MuscleGroupUtil.canonicalize("back"));
    }

    // ── Shoulders ─────────────────────────────────────────────────────

    @Test
    void shoulders_exact_is_idempotent() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("Shoulders"));
    }

    @Test
    void shoulder_singular() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("Shoulder"));
    }

    @Test
    void front_delts() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("Front Delts"));
    }

    @Test
    void rear_delts() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("Rear Delts"));
    }

    @Test
    void delt_singular() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("Delt"));
    }

    @Test
    void delts_lowercase() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("delts"));
    }

    // ── Legs ──────────────────────────────────────────────────────────

    @Test
    void legs_exact_is_idempotent() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Legs"));
    }

    @Test
    void leg_singular() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Leg"));
    }

    @Test
    void quads() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Quads"));
    }

    @Test
    void quadriceps() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Quadriceps"));
    }

    @Test
    void glutes() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Glutes"));
    }

    @Test
    void glute_singular() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Glute"));
    }

    @Test
    void hamstrings() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Hamstrings"));
    }

    @Test
    void hamstring_singular() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Hamstring"));
    }

    @Test
    void calves() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Calves"));
    }

    @Test
    void calf_singular() {
        assertEquals("Legs", MuscleGroupUtil.canonicalize("Calf"));
    }

    // ── Arms ──────────────────────────────────────────────────────────

    @Test
    void arms_exact_is_idempotent() {
        assertEquals("Arms", MuscleGroupUtil.canonicalize("Arms"));
    }

    @Test
    void arm_singular() {
        assertEquals("Arms", MuscleGroupUtil.canonicalize("Arm"));
    }

    @Test
    void biceps() {
        assertEquals("Arms", MuscleGroupUtil.canonicalize("Biceps"));
    }

    @Test
    void bicep_singular() {
        assertEquals("Arms", MuscleGroupUtil.canonicalize("Bicep"));
    }

    @Test
    void triceps() {
        assertEquals("Arms", MuscleGroupUtil.canonicalize("Triceps"));
    }

    @Test
    void tricep_singular() {
        assertEquals("Arms", MuscleGroupUtil.canonicalize("Tricep"));
    }

    // ── Core ──────────────────────────────────────────────────────────

    @Test
    void core_exact_is_idempotent() {
        assertEquals("Core", MuscleGroupUtil.canonicalize("Core"));
    }

    @Test
    void core_lowercase() {
        assertEquals("Core", MuscleGroupUtil.canonicalize("core"));
    }

    @Test
    void abs() {
        assertEquals("Core", MuscleGroupUtil.canonicalize("Abs"));
    }

    @Test
    void abs_lowercase() {
        assertEquals("Core", MuscleGroupUtil.canonicalize("abs"));
    }

    @Test
    void abdominals() {
        assertEquals("Core", MuscleGroupUtil.canonicalize("Abdominals"));
    }

    // ── Trim handling ─────────────────────────────────────────────────

    @Test
    void leading_trailing_whitespace_is_trimmed() {
        assertEquals("Chest", MuscleGroupUtil.canonicalize("  Chest  "));
    }

    @Test
    void internal_spaces_preserved_in_compound_names() {
        assertEquals("Shoulders", MuscleGroupUtil.canonicalize("  Front Delts  "));
    }
}
