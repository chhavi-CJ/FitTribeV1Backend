package com.fittribe.api.dto.response;

/**
 * Subset of user_exercise_bests exposed for client-side mid-workout sparkle.
 * Only fields needed for weight and rep comparison — no 1RM, volume, or hold.
 */
public record UserExerciseBestDto(
        String exerciseId,
        Double bestWtKg,
        Integer bestReps,
        Integer repsAtBestWt
) {}
