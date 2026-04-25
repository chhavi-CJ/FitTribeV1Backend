package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import com.fittribe.api.util.Zones;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class AchievementsController {

    private final JdbcTemplate jdbcTemplate;

    public AchievementsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── GET /api/v1/users/achievements ────────────────────────────────
    @GetMapping("/achievements")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> achievements(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<Map<String, Object>> result = new ArrayList<>();

        // ── Achievement 1: Streak ─────────────────────────────────────
        Map<String, Object> streakRow = jdbcTemplate.queryForMap(
                "SELECT streak, max_streak_ever FROM users WHERE id = ?", userId);
        int currentStreak = toInt(streakRow.get("streak"));
        int maxStreakEver  = toInt(streakRow.get("max_streak_ever"));

        if (currentStreak > 0) {
            String subtitle = currentStreak >= maxStreakEver
                    ? "Personal best — keep going"
                    : "Best was " + maxStreakEver + " days";
            result.add(achievement("STREAK",
                    currentStreak + "-day streak",
                    subtitle));
        }

        // ── Achievement 2: PRs this month ─────────────────────────────
        List<String> prNames = jdbcTemplate.queryForList(
                "SELECT DISTINCT e.name FROM pr_events pe " +
                "JOIN exercises e ON e.id = pe.exercise_id " +
                "WHERE pe.user_id = ? " +
                "  AND pe.superseded_at IS NULL " +
                "  AND pe.created_at >= DATE_TRUNC('month', NOW())",
                String.class, userId);

        int prCount = prNames.size();
        if (prCount > 0) {
            List<String> first3 = prNames.stream().limit(3).collect(Collectors.toList());
            String names = String.join(", ", first3);
            if (prCount > 3) names += " and more";
            result.add(achievement("PR",
                    prCount + " PR" + (prCount > 1 ? "s" : "") + " this month",
                    names));
        }

        // ── Achievement 3: Weekly goal consistency ────────────────────
        int weeklyGoal = toInt(jdbcTemplate.queryForObject(
                "SELECT COALESCE(weekly_goal, 4) FROM users WHERE id = ?",
                Integer.class, userId));

        int consecutive = 0;
        // Start from the most recently COMPLETED ISO week (last week), go back up to 12
        LocalDate todayMonday = LocalDate.now(Zones.APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int w = 1; w <= 12; w++) {
            LocalDate weekStart = todayMonday.minusWeeks(w);
            LocalDate weekEnd   = weekStart.plusDays(7);
            Timestamp from = Timestamp.from(weekStart.atStartOfDay(Zones.APP_ZONE).toInstant());
            Timestamp to   = Timestamp.from(weekEnd.atStartOfDay(Zones.APP_ZONE).toInstant());

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workout_sessions " +
                    "WHERE user_id = ? AND status = 'COMPLETED' " +
                    "  AND finished_at >= ? AND finished_at < ?",
                    Integer.class, userId, from, to);

            if (count != null && count >= weeklyGoal) {
                consecutive++;
            } else {
                break;
            }
        }

        if (consecutive > 0) {
            result.add(achievement("GOAL",
                    "Hit " + weeklyGoal + "-day goal",
                    consecutive + " week" + (consecutive > 1 ? "s" : "") + " in a row"));
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Map<String, Object> achievement(String iconType, String title, String subtitle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",     iconType);
        m.put("title",    title);
        m.put("subtitle", subtitle);
        m.put("iconType", iconType);
        return m;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }
}
