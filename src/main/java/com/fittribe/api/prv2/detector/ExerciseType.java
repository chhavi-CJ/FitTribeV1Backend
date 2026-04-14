package com.fittribe.api.prv2.detector;

/**
 * Enumeration of exercise types.
 * Values match the user_exercise_bests.exercise_type column in the database.
 */
public enum ExerciseType {
    WEIGHTED,
    BODYWEIGHT_UNASSISTED,
    BODYWEIGHT_ASSISTED,
    BODYWEIGHT_WEIGHTED,
    TIMED
}
