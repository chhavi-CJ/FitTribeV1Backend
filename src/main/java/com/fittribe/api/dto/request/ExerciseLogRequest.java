package com.fittribe.api.dto.request;

import java.util.List;

public record ExerciseLogRequest(
        String exerciseId,
        String exerciseName,
        List<SetLogRequest> sets
) {}
