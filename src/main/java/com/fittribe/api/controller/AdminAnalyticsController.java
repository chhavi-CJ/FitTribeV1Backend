package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.response.AnalyticsOverviewResponse;
import com.fittribe.api.dto.response.AnalyticsFunnelResponse;
import com.fittribe.api.dto.response.RetentionCohortRow;
import com.fittribe.api.dto.response.ChurnRiskUserRow;
import com.fittribe.api.dto.response.DailyEventCountRow;
import com.fittribe.api.repository.AnalyticsEventRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AnalyticsEventRepository analyticsEventRepo;
    private final UserRepository userRepo;
    private final JdbcTemplate jdbcTemplate;

    public AdminAnalyticsController(AnalyticsEventRepository analyticsEventRepo,
                                    UserRepository userRepo,
                                    JdbcTemplate jdbcTemplate) {
        this.analyticsEventRepo = analyticsEventRepo;
        this.userRepo = userRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── GET /api/admin/analytics/overview ──────────────────────────────
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<?>> getOverview() {
        try {
            long totalUsers = userRepo.count();
            long newUsersToday = analyticsEventRepo.countNewUsersToday();
            long newUsersThisWeek = analyticsEventRepo.countNewUsersThisWeek();
            long workoutsToday = analyticsEventRepo.countWorkoutsCompletedToday();
            long workoutsThisWeek = analyticsEventRepo.countWorkoutsCompletedThisWeek();
            long dau = analyticsEventRepo.countDailyActiveUsers();
            long wau = analyticsEventRepo.countWeeklyActiveUsers();

            AnalyticsOverviewResponse overview = new AnalyticsOverviewResponse(
                    totalUsers, newUsersToday, newUsersThisWeek,
                    workoutsToday, workoutsThisWeek, dau, wau);

            log.info("Analytics overview: totalUsers={}, newToday={}, dau={}", totalUsers, newUsersToday, dau);
            return ResponseEntity.ok(ApiResponse.success(overview));
        } catch (Exception e) {
            log.error("Error fetching analytics overview", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch overview", "ANALYTICS_ERROR"));
        }
    }

    // ── GET /api/admin/analytics/funnel?from=DATE&to=DATE ───────────────
    @GetMapping("/funnel")
    public ResponseEntity<ApiResponse<?>> getFunnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        try {
            Instant fromDate = from != null ? LocalDate.parse(from).atStartOfDay(IST).toInstant()
                    : LocalDate.now(IST).minusDays(30).atStartOfDay(IST).toInstant();
            Instant toDate = to != null ? LocalDate.parse(to).plusDays(1).atStartOfDay(IST).toInstant()
                    : LocalDate.now(IST).plusDays(1).atStartOfDay(IST).toInstant();

            List<Map<String, Object>> funnelData = analyticsEventRepo.getFunnelData(fromDate, toDate);

            long signupCount = funnelData.stream().filter(m -> m.get("user_id") != null).count();
            long onboardCount = funnelData.stream()
                    .filter(m -> m.get("onboarded_at") != null).count();
            long firstStartCount = funnelData.stream()
                    .filter(m -> m.get("first_started_at") != null).count();
            long firstCompleteCount = funnelData.stream()
                    .filter(m -> m.get("first_completed_at") != null).count();
            long completedWithin48h = funnelData.stream()
                    .filter(m -> m.get("completed_within_48h") != null && (Boolean) m.get("completed_within_48h"))
                    .count();

            double conversionOnboarding = signupCount > 0
                    ? BigDecimal.valueOf(onboardCount * 100.0 / signupCount).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            double conversionFirstStart = signupCount > 0
                    ? BigDecimal.valueOf(firstStartCount * 100.0 / signupCount).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            double conversionFirstComplete = signupCount > 0
                    ? BigDecimal.valueOf(firstCompleteCount * 100.0 / signupCount).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0;
            double conversionWithin48h = signupCount > 0
                    ? BigDecimal.valueOf(completedWithin48h * 100.0 / signupCount).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0;

            AnalyticsFunnelResponse funnel = new AnalyticsFunnelResponse(
                    signupCount, onboardCount, firstStartCount, firstCompleteCount, completedWithin48h,
                    conversionOnboarding, conversionFirstStart, conversionFirstComplete, conversionWithin48h);

            log.info("Funnel: signups={}, onboarded={}, firstStart={}, firstComplete={}",
                    signupCount, onboardCount, firstStartCount, firstCompleteCount);
            return ResponseEntity.ok(ApiResponse.success(funnel));
        } catch (Exception e) {
            log.error("Error fetching funnel data", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch funnel", "ANALYTICS_ERROR"));
        }
    }

    // ── GET /api/admin/analytics/retention?cohort_size=week ─────────────
    @GetMapping("/retention")
    public ResponseEntity<ApiResponse<?>> getRetention(
            @RequestParam(defaultValue = "week") String cohortSize) {
        try {
            List<Map<String, Object>> cohortData = analyticsEventRepo.getRetentionCohort();

            List<RetentionCohortRow> result = cohortData.stream()
                    .map(row -> {
                        LocalDate cohortWeek;
                        Object cohortWeekObj = row.get("cohort_week");
                        if (cohortWeekObj instanceof Timestamp) {
                            cohortWeek = ((Timestamp) cohortWeekObj).toLocalDateTime().toLocalDate();
                        } else if (cohortWeekObj instanceof java.sql.Date) {
                            cohortWeek = ((java.sql.Date) cohortWeekObj).toLocalDate();
                        } else {
                            throw new RuntimeException("Unexpected cohort_week type: " + cohortWeekObj.getClass());
                        }
                        long cohortSize_ = ((Number) row.get("cohort_size")).longValue();
                        long retained1 = ((Number) row.get("retained_week_1")).longValue();
                        long retained2 = ((Number) row.get("retained_week_2")).longValue();
                        long retained3 = ((Number) row.get("retained_week_3")).longValue();
                        long retained4 = ((Number) row.get("retained_week_4")).longValue();

                        double ret1 = cohortSize_ > 0
                                ? BigDecimal.valueOf(retained1 * 100.0 / cohortSize_).setScale(2, RoundingMode.HALF_UP).doubleValue()
                                : 0;
                        double ret2 = cohortSize_ > 0
                                ? BigDecimal.valueOf(retained2 * 100.0 / cohortSize_).setScale(2, RoundingMode.HALF_UP).doubleValue()
                                : 0;
                        double ret3 = cohortSize_ > 0
                                ? BigDecimal.valueOf(retained3 * 100.0 / cohortSize_).setScale(2, RoundingMode.HALF_UP).doubleValue()
                                : 0;
                        double ret4 = cohortSize_ > 0
                                ? BigDecimal.valueOf(retained4 * 100.0 / cohortSize_).setScale(2, RoundingMode.HALF_UP).doubleValue()
                                : 0;

                        return new RetentionCohortRow(cohortWeek, cohortSize_, retained1, retained2, retained3, retained4,
                                ret1, ret2, ret3, ret4);
                    })
                    .collect(Collectors.toList());

            log.info("Retention cohorts: {} rows", result.size());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error fetching retention cohorts", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch retention", "ANALYTICS_ERROR"));
        }
    }

    // ── GET /api/admin/analytics/churn-risk ───────────────────────────
    @GetMapping("/churn-risk")
    public ResponseEntity<ApiResponse<?>> getChurnRisk() {
        try {
            List<Map<String, Object>> churnUsers = analyticsEventRepo.getChurnRiskUsers();

            List<ChurnRiskUserRow> result = churnUsers.stream()
                    .map(row -> {
                        UUID userId = (UUID) row.get("user_id");
                        String displayName = (String) row.get("display_name");
                        Timestamp lastWorkout = (Timestamp) row.get("last_workout_date");
                        Instant lastWorkoutDate = lastWorkout != null ? lastWorkout.toInstant() : null;
                        int streak = row.get("streak") != null ? ((Number) row.get("streak")).intValue() : 0;
                        String groupName = null;

                        // Optionally fetch group name if user is in a group
                        try {
                            String gname = jdbcTemplate.queryForObject(
                                    "SELECT g.name FROM \"groups\" g " +
                                    "JOIN group_members gm ON g.id = gm.group_id " +
                                    "WHERE gm.user_id = ? LIMIT 1",
                                    String.class, userId);
                            groupName = gname;
                        } catch (Exception ignored) {
                            // User not in any group
                        }

                        return new ChurnRiskUserRow(userId, displayName, lastWorkoutDate, streak, groupName);
                    })
                    .collect(Collectors.toList());

            log.info("Churn risk users: {} users", result.size());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error fetching churn risk users", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch churn risk", "ANALYTICS_ERROR"));
        }
    }

    // ── GET /api/admin/analytics/events/daily?event=EVENT_NAME&days=30 ──
    @GetMapping("/events/daily")
    public ResponseEntity<ApiResponse<?>> getDailyEventCounts(
            @RequestParam String event,
            @RequestParam(defaultValue = "30") int days) {
        try {
            if (event == null || event.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Event name required", "INVALID_REQUEST"));
            }

            if (days <= 0 || days > 365) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Days must be between 1 and 365", "INVALID_REQUEST"));
            }

            List<Map<String, Object>> eventCounts = analyticsEventRepo.getDailyEventCounts(event, days);

            List<DailyEventCountRow> result = eventCounts.stream()
                    .map(row -> {
                        Date eventDate = (Date) row.get("event_date");
                        long eventCount = ((Number) row.get("event_count")).longValue();
                        return new DailyEventCountRow(eventDate.toLocalDate(), eventCount);
                    })
                    .collect(Collectors.toList());

            log.info("Daily event counts for {}: {} days", event, result.size());
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error fetching daily event counts for event={}", event, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch event counts", "ANALYTICS_ERROR"));
        }
    }
}
