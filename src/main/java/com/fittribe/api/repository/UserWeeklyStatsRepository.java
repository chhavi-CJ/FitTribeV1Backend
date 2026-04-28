package com.fittribe.api.repository;

import com.fittribe.api.entity.UserWeeklyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserWeeklyStatsRepository extends JpaRepository<UserWeeklyStats, UUID> {

    Optional<UserWeeklyStats> findByUserIdAndWeekStartDate(UUID userId, LocalDate weekStartDate);

    /** Used by baseline computation: last N weeks of stats for this user. */
    List<UserWeeklyStats> findByUserIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(
            UUID userId, LocalDate from, LocalDate to);

    /** Last 4 completed weeks before {@code before} — used by MostImprovedCalculator for trend analysis. */
    @Query(value = """
        SELECT * FROM user_weekly_stats
        WHERE user_id = :userId
          AND week_start_date < :before
        ORDER BY week_start_date DESC
        LIMIT 4
        """, nativeQuery = true)
    List<UserWeeklyStats> findLast4WeeksBefore(@Param("userId") UUID userId,
                                               @Param("before") LocalDate before);

    /** Batch-fetch stats rows for a set of users and a single week. */
    List<UserWeeklyStats> findByUserIdInAndWeekStartDate(Collection<UUID> userIds, LocalDate weekStartDate);

    /**
     * Upserts a weekly stats row. ON CONFLICT on (user_id, week_start_date) updates all
     * computed fields and refreshes computed_at. Called by WeeklyStatsComputeJob.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_weekly_stats
            (id, user_id, week_start_date, sessions_count, total_volume_kg, prs_hit,
             weekly_goal_target, weekly_goal_hit, sessions_with_3plus_sets,
             sessions_45min_plus, baseline_volume_kg, computed_at)
        VALUES (gen_random_uuid(), :userId, :weekStartDate, :sessionsCount, :totalVolumeKg,
                :prsHit, :weeklyGoalTarget, :weeklyGoalHit, :sessionsWith3PlusSets,
                :sessions45MinPlus, :baselineVolumeKg, NOW())
        ON CONFLICT (user_id, week_start_date) DO UPDATE SET
            sessions_count           = EXCLUDED.sessions_count,
            total_volume_kg          = EXCLUDED.total_volume_kg,
            prs_hit                  = EXCLUDED.prs_hit,
            weekly_goal_target       = EXCLUDED.weekly_goal_target,
            weekly_goal_hit          = EXCLUDED.weekly_goal_hit,
            sessions_with_3plus_sets = EXCLUDED.sessions_with_3plus_sets,
            sessions_45min_plus      = EXCLUDED.sessions_45min_plus,
            baseline_volume_kg       = EXCLUDED.baseline_volume_kg,
            computed_at              = NOW()
        """, nativeQuery = true)
    void upsert(
            @Param("userId")                UUID       userId,
            @Param("weekStartDate")         LocalDate  weekStartDate,
            @Param("sessionsCount")         int        sessionsCount,
            @Param("totalVolumeKg")         java.math.BigDecimal totalVolumeKg,
            @Param("prsHit")                int        prsHit,
            @Param("weeklyGoalTarget")      int        weeklyGoalTarget,
            @Param("weeklyGoalHit")         boolean    weeklyGoalHit,
            @Param("sessionsWith3PlusSets") int        sessionsWith3PlusSets,
            @Param("sessions45MinPlus")     int        sessions45MinPlus,
            @Param("baselineVolumeKg")      java.math.BigDecimal baselineVolumeKg
    );
}
