package com.fittribe.api.findings;

import java.util.EnumSet;
import java.util.Set;

/**
 * Maps raw {@code exercises.muscle_group} / {@code exercises.secondary_muscles}
 * catalog values to {@link WeeklyReportMuscle} tiles.
 *
 * <h3>Smart-filter pattern</h3>
 * The catalog has 11 {@code muscle_group} values (CHEST, BACK, SHOULDERS,
 * BICEPS, TRICEPS, LEGS, HAMSTRINGS, CORE, GLUTES, CALVES, FULL_BODY) but
 * the Weekly Summary only shows 8 tiles. Three primary groups — GLUTES,
 * CALVES, and FULL_BODY — do not get their own tile. Exercises whose
 * primary muscle falls into those buckets contribute to coverage ONLY
 * via their {@code secondary_muscles} array.
 *
 * Concretely, a user who only did calf raises all week gets zero leg
 * coverage on the report (CALVES primary → null tile, and most calf
 * exercises have no LEGS secondary). That's the correct signal — the
 * quads and hamstrings really were untrained.
 *
 * Meanwhile, a user who bench-presses (CHEST primary, TRICEPS secondary)
 * gets credit on BOTH the CHEST and TRICEPS tiles in the same session.
 *
 * @see WeekDataBuilder#build(java.util.UUID, java.time.LocalDate) for the
 *      callsite that populates {@code sessionsByMuscle}.
 */
public final class MuscleMapper {

    private MuscleMapper() {}

    /**
     * Maps a primary {@code muscle_group} value to its report tile.
     * Returns null if the group does not have its own tile (GLUTES,
     * CALVES, FULL_BODY) — the caller should fall through to secondary
     * muscles for those exercises.
     *
     * Accepts both the all-caps tokens used in {@code muscle_group}
     * (e.g. {@code "CHEST"}, {@code "FULL_BODY"}) AND the title-case
     * tokens used in {@code secondary_muscles} (e.g. {@code "Chest"},
     * {@code "Rear delts"}) by way of {@link #mapToken(String)}.
     */
    public static WeeklyReportMuscle mapPrimary(String catalogGroup) {
        return mapToken(catalogGroup);
    }

    /**
     * Maps a secondary-muscle token to its report tile.
     *
     * The {@code exercises.secondary_muscles} array in the catalog
     * uses a LARGER vocabulary than {@code muscle_group} — it includes
     * specific heads ({@code "Rear delts"}, {@code "Upper chest"}),
     * stabilisers ({@code "Brachialis"}, {@code "Anconeus"}), and
     * accessory groups ({@code "Rhomboids"}, {@code "Erector spinae"}).
     * This method maps each to its best-fit tile, returning null for
     * anything that shouldn't contribute to coverage (forearms,
     * calves, glutes, hip flexors, etc.).
     */
    public static WeeklyReportMuscle mapSecondary(String secondaryMuscle) {
        return mapToken(secondaryMuscle);
    }

    /**
     * Unified mapping for any muscle token found in either
     * {@code muscle_group} or {@code secondary_muscles}. Case-insensitive
     * and whitespace-insensitive.
     */
    private static WeeklyReportMuscle mapToken(String token) {
        if (token == null) return null;
        String n = token.trim().toLowerCase(java.util.Locale.ROOT);
        if (n.isEmpty()) return null;
        return switch (n) {
            // Chest family
            case "chest", "upper chest" -> WeeklyReportMuscle.CHEST;

            // Back family — lats, upper/mid back, lower back
            case "back", "lats",
                 "rhomboids", "traps", "upper traps",
                 "teres major", "erector spinae"
                    -> WeeklyReportMuscle.BACK_LATS;

            // Shoulders family — all three delt heads
            case "shoulders", "front delts", "rear delts"
                    -> WeeklyReportMuscle.SHOULDERS;

            // Biceps family — bicep heads plus elbow flexors that work with it
            case "biceps", "biceps short head",
                 "brachialis", "brachioradialis"
                    -> WeeklyReportMuscle.BICEPS;

            // Triceps family — triceps plus tiny elbow-extensor accessory
            case "triceps", "anconeus" -> WeeklyReportMuscle.TRICEPS;

            // Quads / legs — catch-all "Legs" and quad-specific muscles
            case "legs", "quads", "rectus femoris"
                    -> WeeklyReportMuscle.LEGS_QUADS;

            // Hamstrings
            case "hamstrings" -> WeeklyReportMuscle.HAMSTRINGS;

            // Core family — rectus abdominis implied; obliques + deep core
            case "core", "obliques", "transverse abdominis"
                    -> WeeklyReportMuscle.CORE;

            // Smart-filter: explicitly do NOT map to any tile.
            // These either contribute via their own secondary_muscles (CALVES,
            // GLUTES, FULL_BODY at the primary level) or are too peripheral
            // (forearms, hip flexors, stabilisers) to count as trained muscles.
            case "glutes", "calves", "full_body",
                 "soleus", "achilles tendon",
                 "hip flexors", "adductors",
                 "forearms", "forearm flexors", "forearm extensors",
                 "external rotators", "serratus anterior"
                    -> null;

            default -> null;
        };
    }

    /**
     * Returns the set of report tiles trained by one exercise, applying
     * the smart-filter rules described at the class level.
     *
     * <ul>
     *   <li>Primary muscle → one tile (if non-null after filter).</li>
     *   <li>Every secondary muscle → additional tile(s).</li>
     * </ul>
     *
     * Never returns null. Returns an empty set for an exercise whose
     * primary and all secondaries are unmapped (e.g. FULL_BODY with no
     * useful secondary_muscles — shouldn't occur for seeded exercises).
     */
    public static Set<WeeklyReportMuscle> musclesFor(String primary, String[] secondary) {
        Set<WeeklyReportMuscle> out = EnumSet.noneOf(WeeklyReportMuscle.class);
        WeeklyReportMuscle p = mapPrimary(primary);
        if (p != null) out.add(p);
        if (secondary != null) {
            for (String s : secondary) {
                WeeklyReportMuscle m = mapSecondary(s);
                if (m != null) out.add(m);
            }
        }
        return out;
    }
}
