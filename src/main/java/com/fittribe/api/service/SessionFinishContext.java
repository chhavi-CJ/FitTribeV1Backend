package com.fittribe.api.service;

import com.fittribe.api.prv2.detector.LoggedSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of values a finished session needs for its
 * post-processing pipeline. Built on the HTTP thread inside
 * SessionController.finishSession from values already in scope, then
 * passed across the thread boundary to SessionFinishPostProcessor
 * (which runs on prDetectionExecutor).
 *
 * Contains ONLY primitives, UUIDs, and already-loaded value objects.
 * NO JPA entities — those would be detached on the async thread and
 * reading their fields would fail (or worse, lazily fetch on a closed
 * session).
 *
 * The pipeline may load the WorkoutSession entity fresh on the async
 * thread when needed (e.g. for the WORKOUT_FINISHED feed event).
 */
public record SessionFinishContext(
        UUID userId,
        UUID sessionId,
        int weekNumber,
        boolean weeklyGoalHit,
        int completedThisWeek,
        int currentStreak,
        int previousStreak,
        BigDecimal totalVolumeKg,
        int totalSets,
        List<LoggedSet> loggedSets,
        Instant finishedAt,
        int weeklyGoal,
        Instant weekFrom,
        Instant weekTo
) {}
