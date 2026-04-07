package com.fittribe.api.dto.request;

import java.util.List;

public record SaveRoutineRequest(
        String name,
        List<RoutineExerciseInput> exercises
) {
    public record RoutineExerciseInput(
            String exerciseId,
            Integer sets
    ) {}
}
