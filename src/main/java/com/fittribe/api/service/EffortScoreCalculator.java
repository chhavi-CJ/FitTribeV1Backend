package com.fittribe.api.service;

import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Computes the Effort Score for a user in a given week.
 *
 * Components (max 100 total):
 *   Sessions   0–40 pts  (base: up to goal × 8; stretch: up to 2 extra × 4)
 *   Intensity  0–30 pts  (volume vs baseline; 15 neutral for new users)
 *   Progression 0–20 pts (5 per qualifying PR, max 4)
 *   Hard work  0–10 pts  (2 per session meeting BOTH 3+sets AND 45min+, max 5)
 */
@Service
public class EffortScoreCalculator {

    private final UserWeeklyStatsRepository statsRepo;

    public EffortScoreCalculator(UserWeeklyStatsRepository statsRepo) {
        this.statsRepo = statsRepo;
    }

    /**
     * Returns the effort score for {@code userId} in the week starting on {@code weekStart} (Monday).
     * Returns 0 if the user logged no sessions that week.
     */
    public int calculateEffortScore(UUID userId, LocalDate weekStart) {
        UserWeeklyStats stats = statsRepo.findByUserIdAndWeekStartDate(userId, weekStart).orElse(null);
        if (stats == null || stats.getSessionsCount() == 0) return 0;

        int weeklyGoal = stats.getWeeklyGoalTarget();

        // ── Sessions points (0–40) ────────────────────────────────────────────
        int baseSessions    = Math.min(weeklyGoal, stats.getSessionsCount()) * 8;
        int stretchSessions = Math.min(2, Math.max(0, stats.getSessionsCount() - weeklyGoal)) * 4;
        int sessionsPoints  = Math.min(40, baseSessions + stretchSessions);

        // ── Intensity points (0–30) ───────────────────────────────────────────
        int intensityPoints;
        BigDecimal baseline = stats.getBaselineVolumeKg();
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            intensityPoints = 15; // neutral for users with no history
        } else {
            double ratio = stats.getTotalVolumeKg().doubleValue() / baseline.doubleValue();
            intensityPoints = (int) Math.min(30, ratio * 20);
        }

        // ── Progression points (0–20) — 5 per qualifying PR, max 4 PRs ───────
        int progressionPoints = Math.min(20, stats.getPrsHit() * 5);

        // ── Hard work points (0–10) ───────────────────────────────────────────
        // Requires BOTH 3+sets/exercise AND 45min+ in the same session.
        // We track each independently; use the lower count as the ceiling of
        // sessions that could satisfy both conditions simultaneously.
        int qualifyingSessions = Math.min(stats.getSessionsWith3PlusSets(), stats.getSessions45MinPlus());
        int hardworkPoints     = Math.min(10, qualifyingSessions * 2);

        return sessionsPoints + intensityPoints + progressionPoints + hardworkPoints;
    }
}
