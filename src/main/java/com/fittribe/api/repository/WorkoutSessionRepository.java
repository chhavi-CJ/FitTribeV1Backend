package com.fittribe.api.repository;

import com.fittribe.api.entity.WorkoutSession;
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
import java.util.UUID;

@Repository
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {

    List<WorkoutSession> findByUserIdOrderByStartedAtDesc(UUID userId);

    List<WorkoutSession> findTop20ByUserIdAndStatusOrderByStartedAtDesc(UUID userId, String status);

    List<WorkoutSession> findTop3ByUserIdAndStatusOrderByStartedAtDesc(UUID userId, String status);

    // Used for 8-hour cooldown check: any COMPLETED session finished within the last 8 hours
    Optional<WorkoutSession> findFirstByUserIdAndStatusAndFinishedAtAfter(
            UUID userId, String status, Instant after);

    // Used for pulse: sessions completed today for a set of users
    List<WorkoutSession> findByUserIdInAndStatusAndStartedAtAfter(
            Collection<UUID> userIds, String status, Instant after);

    // Used for GET /sessions/today: most recent session started today (any status)
    Optional<WorkoutSession> findFirstByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
            UUID userId, Instant from, Instant to);

    // Targeted update — only writes ai_insight, never touches status
    @Modifying
    @Transactional
    @Query("UPDATE WorkoutSession w SET w.aiInsight = :insight WHERE w.id = :id")
    void updateAiInsight(@Param("id") UUID id, @Param("insight") String insight);

    // Used for weeklyGoalHit and weekly report: sessions completed in a date range
    int countByUserIdAndStatusAndFinishedAtBetween(UUID userId, String status, Instant from, Instant to);

    List<WorkoutSession> findByUserIdAndStatusAndFinishedAtBetween(
            UUID userId, String status, Instant from, Instant to);

    /** Used by the streak reset job: did this user complete any session in a time window? */
    boolean existsByUserIdAndStatusAndFinishedAtBetween(
            UUID userId, String status, Instant from, Instant to);

    /** Total completed sessions ever for a user — used in profile response. */
    int countByUserIdAndStatus(UUID userId, String status);

    /**
     * Returns the historical max weight ever logged for a given exercise by this user,
     * read from the JSONB exercises snapshot stored at session finish.
     * Excludes the current session so a finish can be compared against prior history.
     */
    /**
     * Sum of total_volume_kg for all completed sessions in a time window.
     * Used for week-over-week volume improvement detection.
     */
    @Query(value = """
        SELECT COALESCE(SUM(total_volume_kg), 0)
        FROM workout_sessions
        WHERE user_id = :userId
          AND status  = 'COMPLETED'
          AND finished_at >= :from
          AND finished_at <  :to
        """, nativeQuery = true)
    BigDecimal sumVolumeByUserIdAndFinishedAtBetween(
            @Param("userId") UUID    userId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);

    @Query(value = """
        SELECT COALESCE(MAX((ex->>'maxWeightKg')::numeric), 0)
        FROM workout_sessions ws,
             jsonb_array_elements(ws.exercises) ex
        WHERE ws.user_id    = :userId
          AND ws.status     = 'COMPLETED'
          AND ex->>'exerciseId' = :exerciseId
          AND ws.id        != :sessionId
        """, nativeQuery = true)
    BigDecimal findMaxWeightForExercise(
            @Param("userId")     UUID   userId,
            @Param("exerciseId") String exerciseId,
            @Param("sessionId")  UUID   sessionId);
}
