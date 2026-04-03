package com.fittribe.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinishSessionResponse(
        UUID sessionId,
        String name,
        BigDecimal totalVolumeKg,
        int totalSets,
        Integer durationMins,
        Instant finishedAt,
        int streak,
        int coinsEarned,
        boolean weeklyGoalHit,
        int weekNumber,
        int completedThisWeek,
        String aiCoachInsight
) {}
