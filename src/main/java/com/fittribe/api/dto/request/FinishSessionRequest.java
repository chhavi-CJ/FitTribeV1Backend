package com.fittribe.api.dto.request;

import java.util.List;

public record FinishSessionRequest(
        Integer durationMins,
        List<ExerciseLogRequest> exercises
) {}
