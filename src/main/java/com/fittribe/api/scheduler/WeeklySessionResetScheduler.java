package com.fittribe.api.scheduler;

import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sunday end-of-week cleanup: marks every session still IN_PROGRESS as ABANDONED.
 *
 * Runs at 23:59 IST on Sunday — after WeeklyReportCron (23:00 IST) and
 * GroupGoalScheduler (23:59 IST, same minute but registered later). Any session
 * a user started during the week and never finished or discarded is treated as
 * abandoned so it cannot resurface as a resumable session in the new week.
 *
 * This is a backstop: SessionController.todaySession() also self-heals stale
 * IN_PROGRESS sessions on read, but the cron ensures the DB is clean regardless.
 */
@Component
public class WeeklySessionResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklySessionResetScheduler.class);

    private final WorkoutSessionRepository sessionRepo;

    public WeeklySessionResetScheduler(WorkoutSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Scheduled(cron = "0 59 23 * * SUN", zone = "Asia/Kolkata")
    public void resetInProgressSessions() {
        try {
            int count = sessionRepo.abandonAllInProgressSessions();
            log.info("Weekly session reset completed. Sessions abandoned: {}", count);
        } catch (Exception e) {
            log.error("Weekly session reset failed — IN_PROGRESS sessions not abandoned", e);
        }
    }
}
