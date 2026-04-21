package com.fittribe.api.dto.response;

/**
 * PR celebration details returned when isPr=true on a logged set.
 * Null when isPr=false.
 */
public record PrDetails(
        String type,            // "FIRST_EVER" | "WEIGHT_PR" | "REP_PR" | "VOLUME_PR" | "MAX_ATTEMPT"
        double currentValue,    // e.g., 32.5 (kg), 8 (reps), 260 (volume kg)
        Double previousValue,   // e.g., 30.0 — null for FIRST_EVER
        String unit,            // "kg", "reps", "kg" (for volume)
        int coinsEarned         // 5 for WEIGHT_PR, 3 for FIRST_EVER, etc.
) {}
