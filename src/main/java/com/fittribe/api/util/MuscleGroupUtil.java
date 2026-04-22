package com.fittribe.api.util;

/**
 * Shared utility for canonicalising raw muscle_group values from the exercises table.
 *
 * <p>The DB stores variations like "Upper Chest", "Front Delts", "Calves", etc.
 * This maps all variants to the 6 standard taxonomy names used throughout the app:
 * <ul>
 *   <li>Chest</li>
 *   <li>Back</li>
 *   <li>Shoulders</li>
 *   <li>Legs</li>
 *   <li>Arms</li>
 *   <li>Core</li>
 * </ul>
 *
 * <p>Returns an empty string for unmapped values (e.g. "Full Body") so callers
 * can decide how to handle the fallthrough.
 */
public final class MuscleGroupUtil {

    private MuscleGroupUtil() {}

    /**
     * Canonicalise a raw DB {@code muscle_group} value.
     *
     * @param raw the raw string from {@code exercises.muscle_group}; may be null
     * @return canonical group name, or {@code ""} if unmapped / null
     */
    public static String canonicalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String n = raw.trim().toLowerCase();
        if (n.contains("chest"))                                              return "Chest";
        if (n.contains("back"))                                               return "Back";
        if (n.contains("shoulder") || n.contains("delt"))                     return "Shoulders";
        if (n.contains("leg") || n.contains("quad") || n.contains("glute")
                || n.contains("calf") || n.contains("calves")
                || n.contains("hamstring"))                                    return "Legs";
        if (n.contains("arm") || n.contains("bicep") || n.contains("tricep")) return "Arms";
        if (n.contains("core") || n.contains("ab"))                           return "Core";
        return "";   // full_body, unknown, etc.
    }
}
