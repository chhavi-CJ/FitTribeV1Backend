package com.fittribe.api.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key class for {@link ExerciseStrengthTarget}.
 *
 * <p>Field names must exactly match the {@code @Id}-annotated field
 * names in the owning entity — JPA requires this for {@code @IdClass}
 * resolution.
 */
public class ExerciseStrengthTargetId implements Serializable {

    private String exerciseId;
    private String gender;
    private String fitnessLevel;

    public ExerciseStrengthTargetId() {}

    public ExerciseStrengthTargetId(String exerciseId, String gender, String fitnessLevel) {
        this.exerciseId   = exerciseId;
        this.gender       = gender;
        this.fitnessLevel = fitnessLevel;
    }

    public String getExerciseId()   { return exerciseId; }
    public String getGender()       { return gender; }
    public String getFitnessLevel() { return fitnessLevel; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExerciseStrengthTargetId)) return false;
        ExerciseStrengthTargetId that = (ExerciseStrengthTargetId) o;
        return Objects.equals(exerciseId,   that.exerciseId)
            && Objects.equals(gender,       that.gender)
            && Objects.equals(fitnessLevel, that.fitnessLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exerciseId, gender, fitnessLevel);
    }
}
