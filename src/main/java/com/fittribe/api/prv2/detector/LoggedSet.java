package com.fittribe.api.prv2.detector;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A set that a user has logged in the current session.
 * Input to PRDetector.detect().
 *
 * Weight and reps are mutually exclusive based on exercise type:
 * - For WEIGHTED/BODYWEIGHT exercises: weightKg + reps are populated; holdSeconds is null
 * - For TIMED exercises: holdSeconds is populated; weightKg and reps are null
 *
 * setId is the UUID of the corresponding set_logs row. Populated at session finish
 * so that pr_events.set_id can be written, making events addressable by the edit cascade.
 * May be null in test contexts or where set_logs row isn't available.
 */
public record LoggedSet(
    UUID setId,               // nullable in tests; populated from set_logs.id at session finish
    String exerciseId,
    BigDecimal weightKg,      // nullable for TIMED or BODYWEIGHT_UNASSISTED
    Integer reps,              // nullable for TIMED
    Integer holdSeconds       // nullable for weight-based exercises
) {
}
