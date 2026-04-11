package com.fittribe.api.repository;

import com.fittribe.api.entity.ExerciseStrengthTarget;
import com.fittribe.api.entity.ExerciseStrengthTargetId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data-access layer for the {@code exercise_strength_targets} reference
 * table (Flyway V37).
 *
 * <p>Read-only in production — rows are seeded by the V37 migration and
 * never updated by application code. New target rows (INTERMEDIATE /
 * ADVANCED levels) will arrive via future Flyway migrations.
 *
 * <h3>Fallback logic</h3>
 * {@code StrengthScoreService} handles the null-gender and
 * null-fitness-level fallbacks before calling this repository. The repo
 * always receives concrete non-null values; it does not implement
 * fallback chaining itself.
 */
@Repository
public interface ExerciseStrengthTargetRepository
        extends JpaRepository<ExerciseStrengthTarget, ExerciseStrengthTargetId> {

    /**
     * Look up the target for a specific exercise / gender / fitness-level
     * combination.
     *
     * <p>Returns {@code Optional.empty()} when no seed row exists for
     * the combination (e.g., an INTERMEDIATE row that hasn't been added
     * yet). The caller ({@code StrengthScoreService}) treats an empty
     * result as a skip — the exercise is excluded from the score rather
     * than defaulting to an arbitrary value.
     */
    Optional<ExerciseStrengthTarget> findByExerciseIdAndGenderAndFitnessLevel(
            String exerciseId, String gender, String fitnessLevel);
}
