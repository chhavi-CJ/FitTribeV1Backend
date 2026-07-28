package com.fittribe.api.scheduler;

import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled analytics jobs for tracking user behavior and engagement.
 *
 * <p>All times are in IST (Asia/Kolkata, UTC+5:30). Every job wraps per-user
 * work in a try-catch so a single failure never aborts the whole run.
 *
 * <p>Events tracked:
 * <ul>
 *   <li>workout_skipped — daily 11:59 PM IST: user had planned gym day but no session</li>
 *   <li>inactive_Xd — daily midnight IST: user inactive for 7, 14, or 30 days</li>
 * </ul>
 *
 * <p>Note: streak_broken tracking is integrated into WeeklyStreakEvaluationScheduler
 * where the reset actually occurs, ensuring accurate attribution.
 */
@Component
public class AnalyticsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final AnalyticsService        analyticsService;
    private final UserRepository          userRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final UserDayStatusRepository dayStatusRepo;

    public AnalyticsScheduler(AnalyticsService analyticsService,
                              UserRepository userRepo,
                              WorkoutSessionRepository sessionRepo,
                              UserDayStatusRepository dayStatusRepo) {
        this.analyticsService = analyticsService;
        this.userRepo = userRepo;
        this.sessionRepo = sessionRepo;
        this.dayStatusRepo = dayStatusRepo;
    }

    /**
     * Daily at 23:59:00 IST — detect workout skips.
     *
     * <p>For each user: if today is a planned gym day (UserDayStatus.status == null or "ACTIVE")
     * AND the user has no COMPLETED session with finished_at today, track "workout_skipped".
     */
    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Kolkata")
    public void workoutSkippedJob() {
        LocalDate today = LocalDate.now(IST);
        Instant dayStart = today.atStartOfDay(IST).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(IST).toInstant();

        List<User> allUsers = userRepo.findAll();

        log.info("AnalyticsScheduler: workoutSkipped starting for {} users", allUsers.size());

        int skipped = 0;
        for (User user : allUsers) {
            try {
                // Check if today is a planned gym day
                Optional<UserDayStatus> dayStatus = dayStatusRepo.findByIdUserIdAndIdDate(user.getId(), today);
                String status = dayStatus.map(UserDayStatus::getStatus).orElse(null);

                // Planned gym day if status is null or "ACTIVE" (not REST, SICK, TRAVELLING)
                boolean plannedGymDay = status == null || "ACTIVE".equals(status);

                if (!plannedGymDay) {
                    continue;
                }

                // Check if user has no session today
                boolean hasSessionToday = sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                        user.getId(), "COMPLETED", dayStart, dayEnd);

                if (!hasSessionToday) {
                    analyticsService.track(user.getId(), "workout_skipped",
                            Map.of("date", today.toString()));
                    skipped++;
                }

            } catch (Exception e) {
                log.warn("AnalyticsScheduler: workoutSkipped failed for userId={}: {}",
                        user.getId(), e.getMessage());
            }
        }

        log.info("AnalyticsScheduler: workoutSkipped complete — {} events tracked", skipped);
    }

    /**
     * Daily at 00:00:00 IST — detect inactive users and churn risk.
     *
     * <p>Queries users whose last COMPLETED session finished_at was:
     * <ul>
     *   <li>exactly 7 days ago → track "inactive_7d"</li>
     *   <li>exactly 14 days ago → track "inactive_14d"</li>
     *   <li>exactly 30 days ago → track "inactive_30d"</li>
     * </ul>
     *
     * <p>"Exactly" is defined as finished_at within a 24-hour window (to account for
     * query timing variance). Multiple events can be tracked for the same user across
     * different days (e.g., a user tracked as "inactive_7d" on day 7, then "inactive_14d"
     * on day 14).
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void churnRiskJob() {
        LocalDate today = LocalDate.now(IST);
        Instant now = Instant.now();

        log.info("AnalyticsScheduler: churnRisk starting for inactivity detection");

        // Detect 7-day, 14-day, and 30-day inactivity
        trackInactiveWindow(today, 7, now);
        trackInactiveWindow(today, 14, now);
        trackInactiveWindow(today, 30, now);

        log.info("AnalyticsScheduler: churnRisk complete");
    }

    /**
     * Helper: find users whose last session finished X days ago (within a 24-hour window)
     * and track the corresponding inactivity event.
     */
    private void trackInactiveWindow(LocalDate today, int daysBefore, Instant now) {
        Instant targetEnd = now.minus(daysBefore, ChronoUnit.DAYS);
        Instant windowStart = targetEnd.minus(1, ChronoUnit.DAYS);
        Instant windowEnd = targetEnd.plus(1, ChronoUnit.DAYS);

        List<User> allUsers = userRepo.findAll();
        int tracked = 0;

        for (User user : allUsers) {
            try {
                // Find if user's most recent session finished within the target window
                // We check if there's a session in the window AND nothing more recent
                boolean hasSessionInWindow = sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                        user.getId(), "COMPLETED", windowStart, windowEnd);

                if (!hasSessionInWindow) {
                    continue;
                }

                // Verify there's no more recent session
                boolean hasMoreRecentSession = sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                        user.getId(), "COMPLETED", windowEnd, now);

                if (hasMoreRecentSession) {
                    // User trained more recently, skip
                    continue;
                }

                // This user's last session was X days ago
                String eventName = "inactive_" + daysBefore + "d";
                analyticsService.track(user.getId(), eventName,
                        Map.of("days_inactive", daysBefore,
                               "last_workout_days_ago", daysBefore));
                tracked++;

            } catch (Exception e) {
                log.warn("AnalyticsScheduler: churnRisk({} days) failed for userId={}: {}",
                        daysBefore, user.getId(), e.getMessage());
            }
        }

        log.debug("AnalyticsScheduler: churnRisk({} days) tracked {} events", daysBefore, tracked);
    }
}
