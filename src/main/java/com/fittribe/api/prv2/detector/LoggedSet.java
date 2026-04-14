package com.fittribe.api.prv2.detector;

import java.math.BigDecimal;

/**
 * A set that a user has logged in the current session.
 * Input to PRDetector.detect().
 *
 * Weight and reps are mutually exclusive based on exercise type:
 * - For WEIGHTED/BODYWEIGHT exercises: weightKg + reps are populated; holdSeconds is null
 * - For TIMED exercises: holdSeconds is populated; weightKg and reps are null
 */
public record LoggedSet(
    String exerciseId,
    BigDecimal weightKg,      // nullable for TIMED or BODYWEIGHT_UNASSISTED
    Integer reps,              // nullable for TIMED
    Integer holdSeconds       // nullable for weight-based exercises
) {
}
