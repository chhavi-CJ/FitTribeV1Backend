package com.fittribe.api.repository;

import com.fittribe.api.entity.UserExerciseBests;
import com.fittribe.api.entity.UserExerciseBestsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for {@code user_exercise_bests} table (Flyway V44).
 *
 * <p>Mutable cache of per-user, per-exercise maxima. Used by PRDetector
 * during set logging to determine if a new set is a PR. Rebuilt from
 * session history by the nightly integrity job.
 */
@Repository
public interface UserExerciseBestsRepository extends JpaRepository<UserExerciseBests, UserExerciseBestsId> {

    /**
     * Fetch the best known record for a single user-exercise pair.
     * Returns empty if the user has never logged this exercise.
     */
    Optional<UserExerciseBests> findByUserIdAndExerciseId(UUID userId, String exerciseId);

    /** All bests for a user — used by GET /users/me/exercise-bests for client-side sparkle. */
    List<UserExerciseBests> findByUserId(UUID userId);

    /** Count distinct exercises with a recorded best for this user — used in profile response. */
    int countByUserId(UUID userId);
}
