package com.fittribe.api.repository;

import com.fittribe.api.entity.SetLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SetLogRepository extends JpaRepository<SetLog, UUID> {

    List<SetLog> findBySessionId(UUID sessionId);

    @Transactional
    void deleteBySessionId(UUID sessionId);

    @Transactional
    void deleteBySessionIdAndExerciseIdAndSetNumber(UUID sessionId, String exerciseId, int setNumber);

    @Transactional
    void deleteBySessionIdAndExerciseId(UUID sessionId, String exerciseId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO set_logs
            (id, session_id, exercise_id, exercise_name,
             set_number, weight_kg, reps, is_pr, logged_at)
        VALUES (gen_random_uuid(), :sessionId, :exerciseId,
            :exerciseName, :setNumber, :weightKg, :reps,
            false, now())
        ON CONFLICT (session_id, exercise_id, set_number)
        DO UPDATE SET
            weight_kg  = EXCLUDED.weight_kg,
            reps       = EXCLUDED.reps,
            logged_at  = now()
        """, nativeQuery = true)
    void upsertSetLog(
            @Param("sessionId")    UUID       sessionId,
            @Param("exerciseId")   String     exerciseId,
            @Param("exerciseName") String     exerciseName,
            @Param("setNumber")    int        setNumber,
            @Param("weightKg")     BigDecimal weightKg,
            @Param("reps")         int        reps
    );

    /**
     * PR check: find the heaviest set this user has ever logged for this exercise
     * across all COMPLETED sessions. Uses a native join because set_logs
     * does not have a direct user_id column.
     */
    // Used for pulse hasPr: returns the subset of session IDs that contain at least one PR set
    @Query("SELECT DISTINCT sl.sessionId FROM SetLog sl WHERE sl.sessionId IN :ids AND sl.isPr = true")
    Set<UUID> findSessionIdsWithPrIn(@Param("ids") Collection<UUID> ids);

    // Kept for any existing callers — delegates to the new query
    default boolean existsBySessionIdInAndIsPrTrue(Collection<UUID> sessionIds) {
        return !findSessionIdsWithPrIn(sessionIds).isEmpty();
    }

    // Used for plan history: all sets across multiple session IDs
    List<SetLog> findBySessionIdIn(Collection<UUID> sessionIds);

    @Query(value =
        "SELECT sl.* FROM set_logs sl " +
        "JOIN workout_sessions ws ON sl.session_id = ws.id " +
        "WHERE ws.user_id = :userId " +
          "AND sl.exercise_id = :exerciseId " +
          "AND ws.status = 'COMPLETED' " +
        "ORDER BY sl.weight_kg DESC " +
        "LIMIT 1",
        nativeQuery = true)
    Optional<SetLog> findTopByUserIdAndExerciseIdOrderByWeightKgDesc(
            @Param("userId") UUID userId,
            @Param("exerciseId") String exerciseId);
}
