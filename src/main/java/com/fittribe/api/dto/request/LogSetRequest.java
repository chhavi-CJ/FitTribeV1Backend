package com.fittribe.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LogSetRequest(
        @NotBlank String exerciseId,
        String exerciseName,
        @NotNull @Min(1) @Max(20) Integer setNumber,
        BigDecimal weightKg,   // null = bodyweight exercise — validated in controller when non-null
        @NotNull @Min(1) @Max(100) Integer reps
) {}
