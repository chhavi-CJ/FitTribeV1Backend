package com.fittribe.api.dto.exercise;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One entry per exercise in the response of GET /api/v1/exercises/last-logged.
 * Used by the frontend to pre-fill weight + reps when a user starts a workout,
 * so they see "last time you did 7.5kg × 11" instead of empty defaults.
 *
 * "Last logged" = the setNumber=1 set (opening working set) from the user's
 * most recent COMPLETED session that contained the exercise. Source: the
 * workout_sessions.exercises JSONB column — not set_logs, which is wiped
 * by SessionFinishPostProcessor at session finish.
 *
 * Fields:
 *   lastKg          — weightKg of the setNumber=1 set. JSONB may store this
 *                     as an integer or fractional number (e.g. 6.5); the
 *                     native query casts to NUMERIC, so the receiving type
 *                     here is BigDecimal.
 *   lastReps        — reps of the setNumber=1 set. Non-null by construction
 *                     (query filters reps > 0).
 *   lastLoggedAt    — the session's finished_at (there is no per-set
 *                     timestamp in JSONB). Frontend may surface this as
 *                     "Last: 3 days ago".
 */
public record LastLoggedSetDto(
        BigDecimal lastKg,
        Integer    lastReps,
        Instant    lastLoggedAt
) {}
