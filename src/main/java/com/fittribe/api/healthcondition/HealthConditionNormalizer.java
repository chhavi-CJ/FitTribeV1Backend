package com.fittribe.api.healthcondition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps any frontend health condition string to canonical HealthCondition
 * enum name. Called at all write boundaries so the DB only ever stores
 * canonical values.
 *
 * Known inputs (both onboarding full labels and profile edit short ids):
 *   "heart" | "Heart condition"          → HEART_CONDITION
 *   "diabetes" | "Diabetes" | "Type 2 Diabetes" → DIABETES
 *   "thyroid" | "Thyroid" | "Thyroid condition" → THYROID
 *   "joints" | "Joint issues" | "Joint issues (knees/hips)" → JOINT_ISSUES
 *   "back" | "Back pain" | "Lower back pain" → BACK_PAIN
 *   "shoulder" | "Shoulder injury"       → SHOULDER_INJURY
 *   "asthma" | "Asthma"                  → ASTHMA
 *   "pcos" | "PCOS"                      → PCOS
 *   "postnatal" | "Post-natal"           → POSTNATAL
 *   "pregnancy" | "Pregnancy"            → PREGNANCY
 *
 * Unknown values are logged at warn level and dropped.
 */
public final class HealthConditionNormalizer {

    private static final Logger log = LoggerFactory.getLogger(HealthConditionNormalizer.class);

    private HealthConditionNormalizer() {}

    /**
     * Normalize an array of mixed-format health condition strings into
     * canonical enum-name strings. Duplicates removed. Unknown values
     * dropped with a warn log. Null input returns empty array.
     */
    public static String[] normalize(String[] raw) {
        if (raw == null || raw.length == 0) return new String[0];
        Set<HealthCondition> canonical = new LinkedHashSet<>();
        for (String input : raw) {
            HealthCondition mapped = mapOne(input);
            if (mapped != null) {
                canonical.add(mapped);
            } else if (input != null && !input.isBlank()) {
                log.warn("Unknown health condition dropped: '{}'", input);
            }
        }
        return canonical.stream().map(Enum::name).toArray(String[]::new);
    }

    /**
     * Case-insensitive, whitespace-tolerant single-value mapping.
     * Returns null for unknown or blank inputs.
     */
    private static HealthCondition mapOne(String input) {
        if (input == null) return null;
        String n = input.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        return switch (n) {
            case "heart", "heart condition"           -> HealthCondition.HEART_CONDITION;
            case "diabetes", "type 2 diabetes"        -> HealthCondition.DIABETES;
            case "thyroid", "thyroid condition"       -> HealthCondition.THYROID;
            case "joints", "joint issues",
                 "joint issues (knees/hips)"          -> HealthCondition.JOINT_ISSUES;
            case "back", "back pain", "lower back pain" -> HealthCondition.BACK_PAIN;
            case "shoulder", "shoulder injury"        -> HealthCondition.SHOULDER_INJURY;
            case "asthma"                             -> HealthCondition.ASTHMA;
            case "pcos"                               -> HealthCondition.PCOS;
            case "postnatal", "post-natal", "post natal" -> HealthCondition.POSTNATAL;
            case "pregnancy", "pregnant"              -> HealthCondition.PREGNANCY;
            // Also accept already-canonical values (idempotent)
            case "heart_condition" -> HealthCondition.HEART_CONDITION;
            case "joint_issues"    -> HealthCondition.JOINT_ISSUES;
            case "back_pain"       -> HealthCondition.BACK_PAIN;
            case "shoulder_injury" -> HealthCondition.SHOULDER_INJURY;
            default                -> HealthCondition.fromCanonical(n).orElse(null);
        };
    }

    /**
     * Convert canonical enum names back to short ids for frontend.
     * Used by GET /users/health-conditions to preserve UI compatibility.
     * Unknown canonicals dropped silently.
     */
    public static List<String> toShortIds(String[] canonical) {
        if (canonical == null) return List.of();
        return Arrays.stream(canonical)
                .map(HealthCondition::fromCanonical)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(HealthCondition::toShortId)
                .collect(Collectors.toList());
    }
}
