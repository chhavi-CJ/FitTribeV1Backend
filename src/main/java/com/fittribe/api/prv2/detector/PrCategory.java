package com.fittribe.api.prv2.detector;

/**
 * Enumeration of PR event categories.
 * Values match the pr_events.pr_category column in the database.
 */
public enum PrCategory {
    FIRST_EVER,
    WEIGHT_PR,
    REP_PR,
    VOLUME_PR,
    MAX_ATTEMPT
}
