package com.fittribe.api.entity;

/**
 * A user's relationship with the Conscious Matching feature.
 *
 * <p>State machine:
 * <pre>
 *   NONE ──submit-quiz──> QUEUED ──batch-run──> MATCHED ──leave-group──> QUEUED
 *                            └──no-match─────────┘    (back to pool for next batch)
 *
 *   any state ──user-opts-out──> OPTED_OUT
 * </pre>
 *
 * <p>Per the Step 4b.ii design: unmatched users (both deferred seeds and
 * the remainder pool) stay {@code QUEUED} after a batch run, simplifying
 * the eligible-pool query to a single-value filter.
 *
 * <p>Values must stay in sync with the V72 users_matching_status_chk
 * CHECK constraint.
 */
public enum UserMatchingStatus {
    NONE,
    QUEUED,
    MATCHED,
    OPTED_OUT
}
