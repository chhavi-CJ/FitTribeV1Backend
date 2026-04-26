package com.fittribe.api.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Abstraction over the storage layer for recently logged sets, used by AI plan generation.
 *
 * <p>Populated from {@code set_logs} via {@link PlanHistoryService} in commit 1.
 * Commit 2 will switch the backing store to {@code workout_sessions.exercises} JSONB
 * and {@code pr_events}, eliminating the live {@code set_logs} read from the plan
 * generation chain.
 */
public record HistoricalSet(
        UUID sessionId,
        Instant finishedAt,
        String exerciseId,
        String exerciseName,
        String muscleGroup,
        BigDecimal weightKg,
        Integer reps,
        Integer setNumber,
        UUID setId,
        boolean isPr
) {}
