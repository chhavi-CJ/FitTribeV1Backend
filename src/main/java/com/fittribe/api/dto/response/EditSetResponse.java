package com.fittribe.api.dto.response;

import java.util.UUID;

/**
 * Response for PATCH /sessions/{id}/log-set/{exerciseId}/{setNumber}.
 *
 * <p>Contains the full updated session (same shape as GET /sessions/today)
 * plus the edited set's ID and PR flag for backward compatibility with
 * any mid-workout callers that only need setId + isPr.
 */
public record EditSetResponse(
        UUID setId,
        boolean isPr,
        TodaySessionResponse session
) {}
