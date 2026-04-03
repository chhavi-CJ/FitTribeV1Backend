package com.fittribe.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TodaySessionResponse(
        UUID sessionId,
        String dayType,
        String muscleGroups,
        BigDecimal kgVolume,
        int sets,
        Integer durationMinutes,
        String aiCoachInsight,
        String status
) {}
