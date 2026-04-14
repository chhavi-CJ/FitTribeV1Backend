package com.fittribe.api.prv2.detector;

import java.util.Map;

/**
 * Result of PR detection for a logged set.
 * Returned by PRDetector.detect().
 */
public record PRResult(
    boolean isPR,
    PrCategory category,              // null if isPR is false
    Map<String, Boolean> signalsMet,  // e.g., { "weight": true, "rep": false, ... }
    Map<String, Object> valuePayload, // structured details: delta_kg, previous_best, new_best, etc.
    int suggestedCoins,
    String detectorVersion
) {
}
