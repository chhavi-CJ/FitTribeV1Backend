package com.fittribe.api.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link UserExerciseBests}.
 */
public class UserExerciseBestsId implements Serializable {
    public UUID userId;
    public String exerciseId;

    public UserExerciseBestsId() {}

    public UserExerciseBestsId(UUID userId, String exerciseId) {
        this.userId = userId;
        this.exerciseId = exerciseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserExerciseBestsId that = (UserExerciseBestsId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(exerciseId, that.exerciseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, exerciseId);
    }
}
