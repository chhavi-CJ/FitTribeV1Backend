package com.fittribe.api.dto.request;

import java.math.BigDecimal;

public record UpdateProfileRequest(
        String displayName,
        String gender,
        String goal,
        String fitnessLevel,
        BigDecimal weightKg,
        BigDecimal heightCm,
        Integer weeklyGoal
) {}
