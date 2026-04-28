package com.fittribe.api.scheduler;

import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.util.Zones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs Sunday 23:58 IST — one minute before SundayGroupCardLockJob (23:59).
 * Computes and upserts user_weekly_stats for every leaderboard-eligible user.
 * The card-lock job's TopPerformerService depends on these rows being present.
 */
@Component
public class WeeklyStatsComputeJob {

    private static final Logger log = LoggerFactory.getLogger(WeeklyStatsComputeJob.class);

    private final UserRepository             userRepo;
    private final WorkoutSessionRepository   sessionRepo;
    private final SetLogRepository           setLogRepo;
    private final PrEventRepository          prEventRepo;
    private final UserWeeklyStatsRepository  statsRepo;

    public WeeklyStatsComputeJob(UserRepository userRepo,
                                 WorkoutSessionRepository sessionRepo,
                                 SetLogRepository setLogRepo,
                                 PrEventRepository prEventRepo,
                                 UserWeeklyStatsRepository statsRepo) {
        this.userRepo    = userRepo;
        this.sessionRepo = sessionRepo;
        this.setLogRepo  = setLogRepo;
        this.prEventRepo = prEventRepo;
        this.statsRepo   = statsRepo;
    }

    @Scheduled(cron = "0 58 23 * * SUN", zone = "Asia/Kolkata")
    public void computeWeeklyStats() {
        LocalDate today      = LocalDate.now(Zones.APP_ZONE);
        LocalDate weekStart  = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        runForWeek(weekStart);
    }

    /** Public entry point for admin-triggered runs. weekStart must be the ISO Monday. */
    public int runForWeek(LocalDate weekStart) {
        Instant from = weekStart.atStartOfDay(Zones.APP_ZONE).toInstant();
        Instant to   = weekStart.plusWeeks(1).atStartOfDay(Zones.APP_ZONE).toInstant();

        log.info("WeeklyStatsComputeJob: starting for weekStart={}", weekStart);

        List<UUID> userIds = userRepo.findLeaderboardEligibleUserIds(Instant.now());
        int computed = 0;

        for (UUID userId : userIds) {
            try {
                computeForUser(userId, weekStart, from, to);
                computed++;
            } catch (Exception e) {
                log.error("WeeklyStatsComputeJob: failed for userId={}", userId, e);
            }
        }

        log.info("WeeklyStatsComputeJob: done — computed {} of {} users", computed, userIds.size());
        return computed;
    }

    private void computeForUser(UUID userId, LocalDate weekStart, Instant from, Instant to) {
        // 1. Sessions in the IST week window
        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndFinishedAtBetween(userId, "COMPLETED", from, to);

        int sessionsCount = sessions.size();

        // 2. Total volume: SUM of already-computed column — no JSONB parsing needed
        BigDecimal totalVolume = sessions.stream()
                .map(s -> s.getTotalVolumeKg() != null ? s.getTotalVolumeKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. PRs hit: count active pr_events for sessions in this IST window
        int prsHit = 0;
        if (!sessions.isEmpty()) {
            List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).collect(Collectors.toList());
            prsHit = prEventRepo.countActiveBySessionIdIn(sessionIds);
        }

        // 4. Weekly goal target from user entity (default 4 if null)
        int weeklyGoalTarget = userRepo.findById(userId)
                .map(u -> u.getWeeklyGoal() != null ? u.getWeeklyGoal() : 4)
                .orElse(4);

        // 5. Weekly goal hit
        boolean weeklyGoalHit = sessionsCount >= weeklyGoalTarget;

        // 6. Sessions with avg sets/exercise >= 3 (computed from set_logs)
        int sessionsWith3PlusSets = 0;
        int sessions45MinPlus     = 0;

        for (WorkoutSession session : sessions) {
            // 45-min threshold
            if (session.getDurationMins() != null && session.getDurationMins() >= 45) {
                sessions45MinPlus++;
            }

            // Avg sets per exercise >= 3
            List<SetLog> sets = setLogRepo.findBySessionId(session.getId());
            if (!sets.isEmpty()) {
                Map<String, Long> setsPerExercise = sets.stream()
                        .collect(Collectors.groupingBy(SetLog::getExerciseId, Collectors.counting()));
                double avgSets = setsPerExercise.values().stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0.0);
                if (avgSets >= 3.0) {
                    sessionsWith3PlusSets++;
                }
            }
        }

        // 7. Baseline: average total_volume_kg from last 4 valid (non-zero-session) weeks
        BigDecimal baseline = computeBaseline(userId, weekStart);

        // 8. Upsert
        statsRepo.upsert(userId, weekStart, sessionsCount, totalVolume, prsHit,
                weeklyGoalTarget, weeklyGoalHit, sessionsWith3PlusSets,
                sessions45MinPlus, baseline);
    }

    private BigDecimal computeBaseline(UUID userId, LocalDate currentWeekStart) {
        LocalDate from = currentWeekStart.minusWeeks(8); // look back up to 8 weeks to find 4 valid
        LocalDate to   = currentWeekStart.minusDays(1);

        List<UserWeeklyStats> pastWeeks = statsRepo
                .findByUserIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(userId, from, to);

        List<BigDecimal> validVolumes = pastWeeks.stream()
                .filter(w -> w.getSessionsCount() > 0)
                .map(UserWeeklyStats::getTotalVolumeKg)
                .limit(4)
                .collect(Collectors.toList());

        if (validVolumes.isEmpty()) return null;

        BigDecimal sum = validVolumes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(validVolumes.size()), 2, RoundingMode.HALF_UP);
    }
}
