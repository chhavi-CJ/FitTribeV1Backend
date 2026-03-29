package com.fittribe.api.scheduler;

import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily streak reset job.
 *
 * Runs at 00:05 UTC every day.
 * For every user whose streak > 0, checks whether they completed at least one
 * session yesterday. If not — and if they are not protected by weekly-goal or
 * new-user grace — their streak is reset to 0 (never negative).
 *
 * Rules:
 *   Rule 3 — No session yesterday → streak = 0
 *   Rule 4 — Grace period: skip users created < 24 hours ago
 *   Rule 5 — Weekly goal protection: skip users who already hit goal this week
 */
@Component
public class StreakScheduler {

    private static final Logger log = LoggerFactory.getLogger(StreakScheduler.class);

    private final UserRepository           userRepo;
    private final WorkoutSessionRepository sessionRepo;

    public StreakScheduler(UserRepository userRepo,
                           WorkoutSessionRepository sessionRepo) {
        this.userRepo    = userRepo;
        this.sessionRepo = sessionRepo;
    }

    @Scheduled(cron = "0 5 0 * * *")   // 00:05 UTC daily
    @Transactional
    public void resetMissedStreaks() {

        Instant now = Instant.now();

        // Yesterday window in UTC — the period we check for sessions
        LocalDate yesterday   = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Instant   yesterdayFrom = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant   yesterdayTo   = yesterday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // This week's Monday 00:00 UTC — for weekly goal protection
        LocalDate monday    = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        Instant   weekFrom  = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant   weekTo    = monday.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<User> activeStreakUsers = userRepo.findAllByStreakGreaterThan(0);

        int reset = 0;
        int skippedGrace = 0;
        int skippedGoal  = 0;

        for (User user : activeStreakUsers) {

            // Rule 4 — New-user grace: skip anyone created within the last 24 hours
            if (user.getCreatedAt() != null &&
                    user.getCreatedAt().isAfter(now.minus(24, ChronoUnit.HOURS))) {
                skippedGrace++;
                continue;
            }

            // Rule 5 — Weekly goal protection: skip if they already hit their goal this week
            int weeklyGoal = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;
            int completedThisWeek = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                    user.getId(), "COMPLETED", weekFrom, weekTo);
            if (completedThisWeek >= weeklyGoal) {
                skippedGoal++;
                continue;
            }

            // Rule 3 — No session yesterday → reset streak to 0
            boolean workedOutYesterday = sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                    user.getId(), "COMPLETED", yesterdayFrom, yesterdayTo);
            if (!workedOutYesterday) {
                user.setStreak(0);   // floor is 0 — never negative
                userRepo.save(user);
                reset++;
            }
        }

        log.info("StreakScheduler: checked={} reset={} skipped(grace)={} skipped(goal)={}",
                activeStreakUsers.size(), reset, skippedGrace, skippedGoal);
    }
}
