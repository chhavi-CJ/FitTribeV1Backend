package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record StartSessionRequest(
        @NotBlank String name,
        String badge,
        String source,
        UUID sourceRoutineId,
        List<Map<String, Object>> plannedExercises
) {}
