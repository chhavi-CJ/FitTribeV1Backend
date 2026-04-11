package com.fittribe.api.strengthscore;

/**
 * Static utility to map the 11-value exercise {@code muscle_group} catalog
 * to the 6-value {@code TrendsMuscle} taxonomy used by the Trends tab.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>CHEST → CHEST</li>
 *   <li>BACK → BACK</li>
 *   <li>LEGS, HAMSTRINGS, GLUTES, CALVES → LEGS</li>
 *   <li>SHOULDERS → SHOULDERS</li>
 *   <li>BICEPS → BICEPS</li>
 *   <li>TRICEPS → TRICEPS</li>
 *   <li>CORE, FULL_BODY → null (skipped from scoring)</li>
 * </ul>
 */
public class TrendsMuscleMapper {

    private TrendsMuscleMapper() {
        // Utility class, no instances
    }

    /**
     * Map a catalog muscle_group value to a TrendsMuscle, or null if the
     * group should not participate in strength scoring.
     *
     * @param muscleGroup the raw 11-value catalog group (e.g., "CHEST", "GLUTES")
     * @return the TrendsMuscle bucket, or null if unmapped
     */
    public static TrendsMuscle map(String muscleGroup) {
        if (muscleGroup == null) {
            return null;
        }

        return switch (muscleGroup) {
            case "CHEST" -> TrendsMuscle.CHEST;
            case "BACK" -> TrendsMuscle.BACK;
            case "SHOULDERS" -> TrendsMuscle.SHOULDERS;
            case "BICEPS" -> TrendsMuscle.BICEPS;
            case "TRICEPS" -> TrendsMuscle.TRICEPS;
            case "LEGS", "HAMSTRINGS", "GLUTES", "CALVES" -> TrendsMuscle.LEGS;
            case "CORE", "FULL_BODY" -> null;  // Skip from scoring
            default -> null;  // Unknown group — skip gracefully
        };
    }
}
