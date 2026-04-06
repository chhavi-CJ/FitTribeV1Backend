package com.fittribe.api.dto.request;

public record SwapExerciseRequest(
        String fromExerciseId,
        String toExerciseId,
        String toExerciseName,
        String toMuscleGroup,
        String toEquipment,
        boolean toIsBodyweight
) {}
