package com.fittribe.api.repository;

import com.fittribe.api.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {
    List<AnalyticsEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AnalyticsEvent> findByEventNameOrderByCreatedAtDesc(String eventName);
    List<AnalyticsEvent> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, Instant since);

    @Query(value = """
            SELECT COUNT(DISTINCT user_id) FROM analytics_events
            WHERE DATE(created_at AT TIME ZONE 'Asia/Kolkata') = CURRENT_DATE AT TIME ZONE 'Asia/Kolkata'
            AND event_name = 'app_opened'
            """, nativeQuery = true)
    long countDailyActiveUsers();

    @Query(value = """
            SELECT COUNT(DISTINCT user_id) FROM analytics_events
            WHERE created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')
            AND created_at < DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata') + INTERVAL '7 days'
            """, nativeQuery = true)
    long countWeeklyActiveUsers();

    @Query(value = """
            SELECT COUNT(*) FROM analytics_events
            WHERE event_name = 'signup_completed'
            AND DATE(created_at AT TIME ZONE 'Asia/Kolkata') = CURRENT_DATE AT TIME ZONE 'Asia/Kolkata'
            """, nativeQuery = true)
    long countNewUsersToday();

    @Query(value = """
            SELECT COUNT(*) FROM analytics_events
            WHERE event_name = 'signup_completed'
            AND created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')
            AND created_at < DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata') + INTERVAL '7 days'
            """, nativeQuery = true)
    long countNewUsersThisWeek();

    @Query(value = """
            SELECT COUNT(*) FROM analytics_events
            WHERE event_name = 'workout_completed'
            AND DATE(created_at AT TIME ZONE 'Asia/Kolkata') = CURRENT_DATE AT TIME ZONE 'Asia/Kolkata'
            """, nativeQuery = true)
    long countWorkoutsCompletedToday();

    @Query(value = """
            SELECT COUNT(*) FROM analytics_events
            WHERE event_name = 'workout_completed'
            AND created_at >= DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata')
            AND created_at < DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata') + INTERVAL '7 days'
            """, nativeQuery = true)
    long countWorkoutsCompletedThisWeek();

    @Query(value = """
            SELECT
              SIGN1.user_id,
              SIGN1.signup_date,
              ONBOARD.onboarded_at,
              W_START.first_started_at,
              W_DONE.first_completed_at,
              CASE WHEN W_DONE.first_completed_at IS NOT NULL
                   AND EXTRACT(EPOCH FROM (W_DONE.first_completed_at - SIGN1.signup_date)) / 3600 <= 48
                   THEN true ELSE false END AS completed_within_48h
            FROM (
              SELECT user_id, MIN(created_at) as signup_date
              FROM analytics_events
              WHERE event_name = 'signup_completed'
              AND created_at >= :fromDate AND created_at < :toDate
              GROUP BY user_id
            ) SIGN1
            LEFT JOIN (
              SELECT DISTINCT user_id, MIN(created_at) as onboarded_at
              FROM analytics_events
              WHERE event_name = 'onboarding_completed'
              GROUP BY user_id
            ) ONBOARD ON SIGN1.user_id = ONBOARD.user_id
            LEFT JOIN (
              SELECT DISTINCT user_id, MIN(created_at) as first_started_at
              FROM analytics_events
              WHERE event_name = 'workout_started'
              GROUP BY user_id
            ) W_START ON SIGN1.user_id = W_START.user_id
            LEFT JOIN (
              SELECT DISTINCT user_id, MIN(created_at) as first_completed_at
              FROM analytics_events
              WHERE event_name = 'workout_completed'
              GROUP BY user_id
            ) W_DONE ON SIGN1.user_id = W_DONE.user_id
            """, nativeQuery = true)
    List<Map<String, Object>> getFunnelData(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    @Query(value = """
            SELECT
              DATE_TRUNC('week', signup_date AT TIME ZONE 'Asia/Kolkata') as cohort_week,
              COUNT(DISTINCT user_id) as cohort_size,
              SUM(CASE WHEN w1_completion_count > 0 THEN 1 ELSE 0 END) as retained_week_1,
              SUM(CASE WHEN w2_completion_count > 0 THEN 1 ELSE 0 END) as retained_week_2,
              SUM(CASE WHEN w3_completion_count > 0 THEN 1 ELSE 0 END) as retained_week_3,
              SUM(CASE WHEN w4_completion_count > 0 THEN 1 ELSE 0 END) as retained_week_4
            FROM (
              SELECT
                signup.user_id,
                signup.signup_date,
                SUM(CASE WHEN comp.created_at >= DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '7 days'
                         AND comp.created_at < DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '14 days'
                         THEN 1 ELSE 0 END) as w1_completion_count,
                SUM(CASE WHEN comp.created_at >= DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '14 days'
                         AND comp.created_at < DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '21 days'
                         THEN 1 ELSE 0 END) as w2_completion_count,
                SUM(CASE WHEN comp.created_at >= DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '21 days'
                         AND comp.created_at < DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '28 days'
                         THEN 1 ELSE 0 END) as w3_completion_count,
                SUM(CASE WHEN comp.created_at >= DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '28 days'
                         AND comp.created_at < DATE_TRUNC('week', signup.signup_date AT TIME ZONE 'Asia/Kolkata') + INTERVAL '35 days'
                         THEN 1 ELSE 0 END) as w4_completion_count
              FROM (
                SELECT DISTINCT user_id, MIN(created_at) as signup_date
                FROM analytics_events
                WHERE event_name = 'signup_completed'
                GROUP BY user_id
              ) signup
              LEFT JOIN (
                SELECT user_id, created_at
                FROM analytics_events
                WHERE event_name = 'workout_completed'
              ) comp ON signup.user_id = comp.user_id AND comp.created_at >= signup.signup_date
              GROUP BY signup.user_id, signup.signup_date
            ) cohort_data
            GROUP BY DATE_TRUNC('week', signup_date AT TIME ZONE 'Asia/Kolkata')
            ORDER BY cohort_week DESC
            """, nativeQuery = true)
    List<Map<String, Object>> getRetentionCohort();

    @Query(value = """
            SELECT
              u.id as user_id,
              u.display_name,
              u.streak,
              MAX(ws.finished_at) as last_workout_date,
              COUNT(DISTINCT DATE(ws.finished_at AT TIME ZONE 'Asia/Kolkata')) as days_since_last_workout
            FROM users u
            LEFT JOIN workout_sessions ws ON u.id = ws.user_id AND ws.status = 'COMPLETED'
            WHERE (CURRENT_TIMESTAMP - MAX(ws.finished_at)) >= INTERVAL '5 days'
              AND (CURRENT_TIMESTAMP - MAX(ws.finished_at)) < INTERVAL '8 days'
            GROUP BY u.id, u.display_name, u.streak
            ORDER BY MAX(ws.finished_at) DESC
            """, nativeQuery = true)
    List<Map<String, Object>> getChurnRiskUsers();

    @Query(value = """
            SELECT
              DATE(created_at AT TIME ZONE 'Asia/Kolkata') as event_date,
              COUNT(*) as event_count
            FROM analytics_events
            WHERE event_name = :eventName
              AND created_at >= CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata' - INTERVAL '1 day' * :days
              AND created_at < CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Kolkata' + INTERVAL '1 day'
            GROUP BY DATE(created_at AT TIME ZONE 'Asia/Kolkata')
            ORDER BY event_date DESC
            """, nativeQuery = true)
    List<Map<String, Object>> getDailyEventCounts(@Param("eventName") String eventName, @Param("days") int days);
}
