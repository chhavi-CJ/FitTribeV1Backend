package com.fittribe.api.healthcondition;

import java.util.Optional;

/**
 * Canonical health condition values. Every health condition stored in
 * users.health_conditions must match one of these enum names exactly.
 *
 * Frontend sends mixed formats today:
 *   - Onboarding sends full labels: "Heart condition", "Joint issues",
 *     "Back pain", "Post-natal", etc.
 *   - Profile edit sends short ids: "heart", "joints", "back",
 *     "postnatal", etc.
 *
 * HealthConditionNormalizer accepts both formats and produces canonical
 * uppercase enum names which are the only values that ever reach the DB.
 *
 * Changes to this enum require a migration to backfill DB values for any
 * previously stored values that no longer map cleanly.
 */
public enum HealthCondition {
    HEART_CONDITION,
    DIABETES,
    THYROID,
    JOINT_ISSUES,
    BACK_PAIN,
    SHOULDER_INJURY,
    ASTHMA,
    PCOS,
    POSTNATAL,
    PREGNANCY;

    /**
     * Parse a canonical enum name string back to the enum.
     * Case-insensitive. Returns empty for unknown values.
     */
    public static Optional<HealthCondition> fromCanonical(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(HealthCondition.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Short id used by frontend profile edit page. Used by GET endpoint
     * to convert canonical → short id for frontend backward compatibility.
     */
    public String toShortId() {
        return switch (this) {
            case HEART_CONDITION -> "heart";
            case DIABETES        -> "diabetes";
            case THYROID         -> "thyroid";
            case JOINT_ISSUES    -> "joints";
            case BACK_PAIN       -> "back";
            case SHOULDER_INJURY -> "shoulder";
            case ASTHMA          -> "asthma";
            case PCOS            -> "pcos";
            case POSTNATAL       -> "postnatal";
            case PREGNANCY       -> "pregnancy";
        };
    }
}
