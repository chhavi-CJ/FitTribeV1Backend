package com.fittribe.api.repository;

import com.fittribe.api.entity.PrEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for {@code pr_events} table (Flyway V44).
 *
 * <p>Append-only log of every PR event, partitioned by {@code week_start}.
 * Queries filter on {@code WHERE superseded_at IS NULL} for active-events-only
 * lookups (see partial index idx_pr_events_superseded).
 */
@Repository
public interface PrEventRepository extends JpaRepository<PrEvent, UUID> {

    /**
     * Fetch all non-superseded PR events for a user in a given week.
     * Used by weekly report generation and UI display.
     */
    List<PrEvent> findByUserIdAndWeekStartAndSupersededAtIsNull(UUID userId, LocalDate weekStart);

    /**
     * Fetch all non-superseded PR events for a session.
     * Used by session summary screen to display today's highlights.
     */
    List<PrEvent> findBySessionIdAndSupersededAtIsNull(UUID sessionId);

    /**
     * Check if any non-superseded PR events exist for a session.
     * Used by history tab to decide if a session badge should appear.
     */
    boolean existsBySessionIdAndSupersededAtIsNull(UUID sessionId);

    /**
     * Fetch all non-superseded PR events for a specific set in a session.
     * Used by edit cascade (Phase 3b) to find PRs that are invalidated by the edit.
     */
    List<PrEvent> findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(UUID userId, UUID sessionId, UUID setId);

    /**
     * Fetch all non-superseded PR events for a specific exercise in a session.
     * Used by exercise delete cascade (Phase 3b) to revoke all PRs for that exercise.
     */
    List<PrEvent> findByUserIdAndSessionIdAndExerciseIdAndSupersededAtNull(UUID userId, UUID sessionId, String exerciseId);

    /**
     * Fetch all non-superseded PR events for a (user, exercise) across all sessions.
     * Used by rebuildExerciseBests to reconstruct the bests cache from the audit log.
     */
    List<PrEvent> findByUserIdAndExerciseIdAndSupersededAtIsNull(UUID userId, String exerciseId);

    /**
     * Batch fetch all non-superseded PR events for a set of sessions.
     * week_start IN clause is REQUIRED to hit the correct RANGE partitions.
     * week_start is computed as the UTC Monday of session.startedAt —
     * matching PrWritePathService.weekStartFor() exactly.
     */
    @Query("SELECT p FROM PrEvent p WHERE p.userId = :userId " +
           "AND p.sessionId IN :sessionIds " +
           "AND p.weekStart IN :weekStarts " +
           "AND p.supersededAt IS NULL")
    List<PrEvent> findActiveByUserIdAndSessionIdInAndWeekStartIn(
            @Param("userId") UUID userId,
            @Param("sessionIds") Collection<UUID> sessionIds,
            @Param("weekStarts") Collection<LocalDate> weekStarts);

    // TODO(pr-system-v2-followup): consider adding partial index on superseded_by
    // WHERE superseded_at IS NOT NULL if edit volume grows
    /**
     * Find PR events that were superseded by a specific event, filtered by category.
     * Used by the un-supersede step in the edit cascade to restore prior PRs
     * when a superseding edit is itself reverted.
     */
    List<PrEvent> findBySupersededByAndPrCategory(UUID supersededBy, String prCategory);
}
