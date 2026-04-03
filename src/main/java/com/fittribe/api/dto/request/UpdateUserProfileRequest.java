package com.fittribe.api.dto.request;

import java.math.BigDecimal;

public record UpdateUserProfileRequest(
        String displayName,
        BigDecimal currentWeightKg,
        String primaryGoal,
        String fitnessLevel,
        Integer weeklyGoal
) {}
