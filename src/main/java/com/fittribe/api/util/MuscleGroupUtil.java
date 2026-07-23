package com.fittribe.api.util;

/**
 * Shared utility for canonicalising raw muscle_group values from the exercises table.
 *
 * <p>The DB stores both broad groups (Chest, Back, Legs …) and sub-muscles
 * (Quads, Hamstrings, Glutes, Triceps …). Sub-muscles are preserved so that
 * a Legs day with 3 target muscles (Quads, Hamstrings, Glutes) computes 9 exercises
 * instead of collapsing to 3. Broad groups fall through to their canonical name.
 *
 * <p>Returns an empty string for unmapped values (e.g. "Full Body") so callers
 * can decide how to handle the fallthrough.
 */
public final class MuscleGroupUtil {

    private MuscleGroupUtil() {}

    /**
     * Canonicalise a raw DB {@code muscle_group} value.
     *
     * <p>Sub-muscles are returned as distinct strings (e.g. "Quads", "Glutes").
     * Broad groups (e.g. "Legs", "Arms") are returned unchanged.
     *
     * @param raw the raw string from {@code exercises.muscle_group}; may be null
     * @return canonical muscle name, or {@code ""} if unmapped / null
     */
    public static String canonicalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String n = raw.trim().toLowerCase();
        // Sub-muscles — must precede broad-group checks to prevent premature collapse
        if (n.contains("quad"))                              return "Quads";
        if (n.contains("hamstring"))                         return "Hamstrings";
        if (n.contains("glute"))                             return "Glutes";
        if (n.contains("calf") || n.contains("calves"))     return "Calves";
        if (n.contains("front delt"))                        return "Front Delts";
        if (n.contains("rear delt"))                         return "Rear Delts";
        if (n.contains("tricep"))                            return "Triceps";
        if (n.contains("bicep"))                             return "Biceps";
        // Broad groups
        if (n.contains("chest"))                             return "Chest";
        if (n.contains("back"))                              return "Back";
        if (n.contains("shoulder") || n.contains("delt"))   return "Shoulders";
        if (n.contains("leg"))                               return "Legs";
        if (n.contains("arm"))                               return "Arms";
        if (n.contains("core") || n.contains("ab"))         return "Core";
        return "";   // full_body, unknown, etc.
    }
}
