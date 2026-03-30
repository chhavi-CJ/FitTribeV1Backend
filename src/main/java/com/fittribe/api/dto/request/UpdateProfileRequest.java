package com.fittribe.api.dto.request;

import jakarta.validation.constraints.Size;
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
        List<String> healthConditions,
        @Size(max = 500, message = "AI context must be 500 characters or less")
        String aiContext
) {}
