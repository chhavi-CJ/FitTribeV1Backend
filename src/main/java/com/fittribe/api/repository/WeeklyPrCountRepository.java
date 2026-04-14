package com.fittribe.api.repository;

import com.fittribe.api.entity.WeeklyPrCount;
import com.fittribe.api.entity.WeeklyPrCountId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for {@code weekly_pr_counts} table (Flyway V44).
 *
 * <p>Pre-aggregated counters per user per week. Used for efficient display
 * of PR summaries on the weekly report card. Sealing happens via the Sunday
 * cron at {@code WeeklyReportCron}.
 */
@Repository
public interface WeeklyPrCountRepository extends JpaRepository<WeeklyPrCount, WeeklyPrCountId> {

    /**
     * Fetch the aggregated PR count row for a specific user and week.
     * Returns empty if no row exists yet (week not sealed or no PRs).
     */
    Optional<WeeklyPrCount> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);
}
