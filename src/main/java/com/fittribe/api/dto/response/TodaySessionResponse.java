package com.fittribe.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TodaySessionResponse(
        UUID sessionId,
        String name,
        String date,
        BigDecimal totalVolumeKg,
        int totalSets,
        Integer durationMins,
        Instant finishedAt,
        String aiCoachInsight,
        String status,
        int streak,
        int completedThisWeek,
        List<Map<String, Object>> swapLog,
        String source,
        List<Map<String, Object>> plannedExercises,
        FeedbackInfo feedback
) {}
