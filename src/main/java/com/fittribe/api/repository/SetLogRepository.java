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

    // TODO: remove — no production callers after Option Y migration
    @Transactional
    void deleteBySessionIdAndExerciseIdAndSetNumber(UUID sessionId, String exerciseId, int setNumber);

    // TODO: remove — no production callers after Option Y migration
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
     * Returns one row per exercise — the user's "last logged" set for that
     * exercise, derived from workout_sessions.exercises JSONB across all
     * COMPLETED sessions. Used by GET /api/v1/exercises/last-logged to
     * pre-fill weight + reps on the active workout screen.
     *
     * "Last logged" definition: the set with setNumber=1 (the opening
     * working set) from the user's most RECENT completed session that
     * contains that exercise. Picked over "top set" (skewed by drop sets)
     * and "highest setNumber" (often a fatigue/failure set) because
     * setNumber=1 most closely matches what the user will want to start
     * at next time.
     *
     * Source of truth: workout_sessions.exercises is a JSONB array of
     * exercise objects shaped like:
     *   [
     *     {
     *       "exerciseId": "bench-press",
     *       "sets": [
     *         { "setNumber": 1, "weightKg": 5,   "reps": 12, "setId": "..." },
     *         { "setNumber": 2, "weightKg": 5,   "reps": 12, "setId": "..." },
     *         { "setNumber": 3, "weightKg": 5,   "reps": 13, "setId": "..." }
     *       ],
     *       ...
     *     }, ...
     *   ]
     * weightKg may be a JSON number (integer or fractional, e.g. 6.5) — the
     * native cast to NUMERIC handles both. set_logs is NOT queried here
     * because the post-finish pipeline (SessionFinishPostProcessor.deleteSetLogs)
     * deletes those rows once a session is COMPLETED — that table is scratch
     * space for IN_PROGRESS sessions only.
     *
     * Strategy:
     *   1. Unnest the JSONB array into one row per (session, exerciseObj).
     *   2. Extract the setNumber=1 set object via a jsonb subquery (filtering
     *      sets where setNumber = 1; takes the first match, defensive against
     *      malformed data).
     *   3. Skip rows where no setNumber=1 set exists (LEFT-joined NULL) — an
     *      exercise that somehow got logged without a first set isn't usable
     *      for pre-fill anyway.
     *   4. DISTINCT ON (exerciseId) ordered by finished_at DESC picks the
     *      most recent session per exercise.
     *
     * Projection columns must match LastLoggedSetDto order:
     *   exercise_id (text), weight_kg (numeric), reps (int), logged_at (timestamptz).
     * logged_at is the session's finished_at — there's no per-set timestamp
     * stored in JSONB.
     */
    @Query(value = """
        SELECT DISTINCT ON (exercise_id)
            exercise_id,
            weight_kg,
            reps,
            logged_at
        FROM (
            SELECT
                ex->>'exerciseId'                              AS exercise_id,
                (first_set->>'weightKg')::numeric              AS weight_kg,
                (first_set->>'reps')::int                      AS reps,
                ws.finished_at                                 AS logged_at,
                ws.finished_at                                 AS session_finished_at
            FROM workout_sessions ws
            CROSS JOIN LATERAL jsonb_array_elements(ws.exercises) AS ex
            LEFT JOIN LATERAL (
                SELECT s
                FROM jsonb_array_elements(ex->'sets') AS s
                WHERE (s->>'setNumber')::int = 1
                LIMIT 1
            ) AS first_set_row(first_set) ON TRUE
            WHERE ws.user_id  = :userId
              AND ws.status   = 'COMPLETED'
              AND ws.exercises IS NOT NULL
              AND jsonb_typeof(ws.exercises) = 'array'
              AND first_set_row.first_set IS NOT NULL
              AND (first_set_row.first_set->>'reps')::int > 0
        ) per_session
        ORDER BY exercise_id, session_finished_at DESC
        """,
        nativeQuery = true)
    List<Object[]> findLastLoggedPerExerciseForUser(@Param("userId") UUID userId);

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

    /**
     * Max weight logged so far in the current session for a given exercise.
     * Used by sparkle (mid-workout PR celebration) to determine if a new set
     * beats the bar set by prior sets in the same in-progress session.
     *
     * Returns null if no sets logged yet for this (session, exercise) pair
     * or if all logged sets had null weight_kg (bodyweight).
     */
    @Query(value =
        "SELECT MAX(weight_kg) FROM set_logs " +
        "WHERE session_id = :sessionId AND exercise_id = :exerciseId",
        nativeQuery = true)
    BigDecimal findMaxWeightInSessionForExercise(
            @Param("sessionId") UUID sessionId,
            @Param("exerciseId") String exerciseId);

    /**
     * Max reps logged so far in the current session for a given exercise at
     * a specific weight. Used by sparkle to detect REP_PR when a new set
     * has the same weight as the session's prior best but more reps.
     *
     * Returns null if no matching sets exist.
     */
    @Query(value =
        "SELECT MAX(reps) FROM set_logs " +
        "WHERE session_id = :sessionId AND exercise_id = :exerciseId " +
        "  AND weight_kg = :weightKg",
        nativeQuery = true)
    Integer findMaxRepsInSessionForExerciseAtWeight(
            @Param("sessionId") UUID sessionId,
            @Param("exerciseId") String exerciseId,
            @Param("weightKg") BigDecimal weightKg);

}
