package com.fittribe.api.repository;

import com.fittribe.api.entity.PrEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
