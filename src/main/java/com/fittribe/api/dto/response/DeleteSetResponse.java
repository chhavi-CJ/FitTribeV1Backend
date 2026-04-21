package com.fittribe.api.dto.response;

/**
 * Response for DELETE /sessions/{id}/log-set/{exerciseId}/{setNumber}.
 *
 * <p>Contains the full updated session (same shape as GET /sessions/today)
 * plus a deleted flag for backward compatibility.
 */
public record DeleteSetResponse(
        boolean deleted,
        TodaySessionResponse session
) {}
