package com.fittribe.api.dto.request;

import java.math.BigDecimal;
import java.util.List;

public record UpdateProfileRequest(
        String displayName,
        String gender,
        String goal,
        String fitnessLevel,
        BigDecimal weightKg,
        BigDecimal heightCm,
        Integer weeklyGoal,
        List<String> healthConditions
) {}
