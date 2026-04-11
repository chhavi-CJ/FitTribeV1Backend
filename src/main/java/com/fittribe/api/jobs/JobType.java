package com.fittribe.api.jobs;

/**
 * Enumeration of job types that producers can enqueue into
 * {@code pending_jobs}. The enum name is the source of truth for the
 * string stored in {@code pending_jobs.job_type} — use {@link #name()}
 * when writing, and dispatch on the raw string on the read side so that
 * a partially-deployed worker never crashes on an unknown value.
 *
 * <p>Defined in Wynners Implementation Plan step A1.2.
 */
public enum JobType {

    /**
     * Compute and persist a weekly report for a single user.
     * Payload: {@code {"userId": "<uuid>"}}.
     */
    COMPUTE_WEEKLY_REPORT
}
