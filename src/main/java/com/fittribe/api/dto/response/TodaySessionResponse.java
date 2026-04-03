package com.fittribe.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TodaySessionResponse(
        UUID sessionId,
        String name,
        String date,
        BigDecimal totalVolumeKg,
        int totalSets,
        Integer durationMins,
        String aiCoachInsight,
        String status,
        int streak,
        int completedThisWeek
) {}
