package com.fittribe.api.service;

import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes week-over-week volume improvement for a user per the Wynners Club spec.
 *
 * Algorithm summary (spec §"Leaderboard 2 — Most Improved"):
 *   baseline = avg of last 4 weeks' volume, excluding zero-session weeks
 *   improvementPct = (thisWeekVolume / baseline × 100) − 100
 *   displayPct clamped to [0, 200]; isOverCap signals the ">200%" display case.
 */
@Service
public class MostImprovedCalculator {

    public enum MostImprovedStatus {
        RANKED,
        NEW_MEMBER,        // fewer than 2 valid (non-zero) prior weeks of history
        GOAL_NOT_HIT,      // did not hit weekly_goal this week
        ROUTE_TO_COMEBACK, // all 4 previous weeks had 0 sessions — show on Comeback Spotlight
        NOT_ELIGIBLE       // 0 sessions this week or baseline is zero
    }

    public static final class MostImprovedResult {
        public final UUID              userId;
        public final MostImprovedStatus status;
        /** Clamped to [0, 200]; null for non-RANKED entries. */
        public final Integer displayPct;
        /** True when the uncapped value exceeded 200 — drives the "200%+" display string. */
        public final boolean isOverCap;
        /** Raw uncapped percentage used for sort ordering; null for non-RANKED entries. */
        public final Integer internalPct;

        private MostImprovedResult(UUID userId, MostImprovedStatus status,
                                   Integer displayPct, boolean isOverCap, Integer internalPct) {
            this.userId      = userId;
            this.status      = status;
            this.displayPct  = displayPct;
            this.isOverCap   = isOverCap;
            this.internalPct = internalPct;
        }

        public static MostImprovedResult ranked(UUID userId, int displayPct,
                                                boolean isOverCap, int internalPct) {
            return new MostImprovedResult(userId, MostImprovedStatus.RANKED,
                    displayPct, isOverCap, internalPct);
        }

        public static MostImprovedResult newMember(UUID userId) {
            return new MostImprovedResult(userId, MostImprovedStatus.NEW_MEMBER,
                    null, false, null);
        }

        public static MostImprovedResult goalNotHit(UUID userId) {
            return new MostImprovedResult(userId, MostImprovedStatus.GOAL_NOT_HIT,
                    null, false, null);
        }

        public static MostImprovedResult routeToComeback(UUID userId) {
            return new MostImprovedResult(userId, MostImprovedStatus.ROUTE_TO_COMEBACK,
                    null, false, null);
        }

        public static MostImprovedResult notEligible(UUID userId) {
            return new MostImprovedResult(userId, MostImprovedStatus.NOT_ELIGIBLE,
                    null, false, null);
        }
    }

    private final UserWeeklyStatsRepository statsRepo;

    public MostImprovedCalculator(UserWeeklyStatsRepository statsRepo) {
        this.statsRepo = statsRepo;
    }

    public MostImprovedResult calculate(UUID userId, LocalDate weekStart) {
        UserWeeklyStats thisWeek = statsRepo
                .findByUserIdAndWeekStartDate(userId, weekStart)
                .orElse(null);

        if (thisWeek == null || thisWeek.getSessionsCount() == 0) {
            return MostImprovedResult.notEligible(userId);
        }

        if (!thisWeek.isWeeklyGoalHit()) {
            return MostImprovedResult.goalNotHit(userId);
        }

        // Fetch up to 4 previous weeks (all weeks before weekStart, newest first)
        List<UserWeeklyStats> previous = statsRepo.findLast4WeeksBefore(userId, weekStart);

        // Exclude weeks where the user logged no sessions
        List<UserWeeklyStats> valid = previous.stream()
                .filter(s -> s.getSessionsCount() > 0)
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            // All prior rows had 0 sessions (or no rows exist) — returning from long break
            return MostImprovedResult.routeToComeback(userId);
        }

        if (valid.size() < 2) {
            // Not enough history to compute a meaningful baseline
            return MostImprovedResult.newMember(userId);
        }

        BigDecimal baseline = valid.stream()
                .map(UserWeeklyStats::getTotalVolumeKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valid.size()), 2, RoundingMode.HALF_UP);

        if (baseline.compareTo(BigDecimal.ZERO) == 0) {
            return MostImprovedResult.notEligible(userId);
        }

        BigDecimal ratio = thisWeek.getTotalVolumeKg()
                .divide(baseline, 4, RoundingMode.HALF_UP);
        int internalPct = ratio
                .multiply(BigDecimal.valueOf(100))
                .subtract(BigDecimal.valueOf(100))
                .intValue();

        int displayPct  = Math.max(0, Math.min(200, internalPct));
        boolean isOverCap = internalPct > 200;

        return MostImprovedResult.ranked(userId, displayPct, isOverCap, internalPct);
    }
}
