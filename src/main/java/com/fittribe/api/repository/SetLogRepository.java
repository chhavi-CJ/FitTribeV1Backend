package com.fittribe.api.repository;

import com.fittribe.api.entity.SetLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * Top set per exercise for this user across COMPLETED sessions finished
     * in {@code [from, to)}. Returns one row per exercise — the heaviest
     * weight in the window, with the {@code reps} from the set that
     * achieved it. On ties (same max weight for multiple sets), picks the
     * one with the most reps.
     *
     * Used by {@link com.fittribe.api.findings.WeekDataBuilder} to populate
     * {@code previousWeekTopSets} — the input for {@code PrRegressionRule}
     * which needs last week's top set to compare against this week's.
     *
     * Returns native Object[] rows: [exercise_id (String), weight_kg
     * (BigDecimal), reps (Integer)]. Native because {@code DISTINCT ON}
     * is Postgres-specific JPQL has no clean equivalent.
     */
    @Query(value = """
        SELECT DISTINCT ON (sl.exercise_id)
               sl.exercise_id,
               sl.weight_kg,
               sl.reps
        FROM set_logs sl
        JOIN workout_sessions ws ON sl.session_id = ws.id
        WHERE ws.user_id    = :userId
          AND ws.status     = 'COMPLETED'
          AND ws.finished_at >= :from
          AND ws.finished_at <  :to
          AND sl.weight_kg IS NOT NULL
        ORDER BY sl.exercise_id, sl.weight_kg DESC, sl.reps DESC
        """, nativeQuery = true)
    List<Object[]> findTopSetsPerExerciseInWindow(
            @Param("userId") UUID    userId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    /**
     * All-time max weight per exercise for this user across COMPLETED
     * sessions finished strictly before {@code before}, restricted to a
     * given set of exercise IDs. Used to compute the "previous best" for
     * exercises that set a new PR this week.
     *
     * Returns native Object[] rows: [exercise_id (String), max_weight_kg
     * (BigDecimal)]. Returns an empty list if the exerciseIds collection
     * is empty — callers must handle that before invoking.
     */
    @Query(value = """
        SELECT sl.exercise_id, MAX(sl.weight_kg) AS max_weight_kg
        FROM set_logs sl
        JOIN workout_sessions ws ON sl.session_id = ws.id
        WHERE ws.user_id     = :userId
          AND ws.status      = 'COMPLETED'
          AND ws.finished_at <  :before
          AND sl.exercise_id IN (:exerciseIds)
          AND sl.weight_kg IS NOT NULL
        GROUP BY sl.exercise_id
        """, nativeQuery = true)
    List<Object[]> findAllTimeMaxBeforeForExercises(
            @Param("userId")      UUID        userId,
            @Param("before")      Instant     before,
            @Param("exerciseIds") Collection<String> exerciseIds);
}
