package com.fittribe.api.jobs;

import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sunday-night fan-out scheduler for the weekly-report pipeline
 * (Wynners A1.3). Runs once a week at 23:00 IST on Sunday — the
 * "week just ending" is Monday … Sunday in {@code Asia/Kolkata}, so
 * by 23:00 Sunday every user who is going to log anything this week
 * already has.
 *
 * <p>The cron itself does no work: it queries {@code users} for
 * active, non-deleting accounts and enqueues one
 * {@link JobType#COMPUTE_WEEKLY_REPORT} row per user into
 * {@code pending_jobs}. A pool of {@link JobWorker} ticks then drains
 * the queue asynchronously — this keeps the fan-out constant-time and
 * isolates any single user's failure (bad data, OpenAI hiccup) to its
 * own retry/backoff cycle inside the worker instead of cascading
 * through one giant transaction.
 *
 * <h3>Payload shape</h3>
 * <pre>{"userId": "&lt;uuid&gt;", "weekStart": "&lt;iso-date&gt;"}</pre>
 * {@code weekStart} is computed once — the previous IST Monday — and
 * stamped onto every job so every row in the batch targets the same
 * window even if processing spills past midnight.
 *
 * <h3>Resilience</h3>
 * Each enqueue is wrapped in its own try/catch so that a single
 * failing row (e.g. a JSON serialization hiccup, a connection blip
 * mid-insert) does not abort the whole fan-out. The alternative — let
 * the first exception propagate out of the scheduled method — would
 * mean one bad user could cause the entire cohort to be skipped for
 * the week, which is worse than quietly missing one. Failures are
 * logged at {@code ERROR} with the user id; ops surfaces the tail.
 */
@Component
public class WeeklyReportCron {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportCron.class);

    private final UserRepository userRepo;
    private final JobEnqueuer jobEnqueuer;

    public WeeklyReportCron(UserRepository userRepo, JobEnqueuer jobEnqueuer) {
        this.userRepo = userRepo;
        this.jobEnqueuer = jobEnqueuer;
    }

    /**
     * Scheduler entry point. Fires every Sunday at 23:00 IST and
     * delegates to {@link #fanOutActiveUsers()}. Spring's
     * {@code @Scheduled} contract requires a {@code void} return, so
     * the success count is discarded here; use
     * {@link #fanOutActiveUsers()} directly from admin endpoints or
     * tests that need the count.
     *
     * <p>Package-private {@code run()} seam lets unit tests invoke the
     * same entry point without relying on Spring's scheduler.
     */
    @Scheduled(cron = "0 0 23 * * SUN", zone = "Asia/Kolkata")
    public void run() {
        fanOutActiveUsers();
    }

    /**
     * Fan out one {@link JobType#COMPUTE_WEEKLY_REPORT} per active
     * user and return the number of jobs successfully enqueued. This
     * is the shared body called by the Sunday-night {@link #run()}
     * scheduler and by the admin endpoint
     * {@code POST /admin/jobs/trigger-weekly-report} when the body
     * omits {@code userId}.
     *
     * <p>The target week is computed once at the top so every enqueued
     * job targets the same window, even if the loop spills past
     * midnight IST. Each enqueue is isolated in its own try/catch so
     * one bad row (JSON hiccup, transient DB error) can't skip the
     * whole cohort.
     */
    public int fanOutActiveUsers() {
        LocalDate weekStart = JobWorker.previousMondayIst();
        String weekStartIso = weekStart.toString();

        List<UUID> userIds = userRepo.findActiveUserIds();
        log.info("WeeklyReportCron: starting fan-out weekStart={} cohort={}",
                weekStartIso, userIds.size());

        int enqueued = 0;
        for (UUID userId : userIds) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("userId", userId.toString());
                payload.put("weekStart", weekStartIso);
                jobEnqueuer.enqueue(JobType.COMPUTE_WEEKLY_REPORT, payload);
                enqueued++;
            } catch (Exception e) {
                // One bad row must not skip the cohort. JobEnqueuer can
                // only throw on serialization failure or a DB write
                // error; both are per-user, so keep going.
                log.error("WeeklyReportCron: failed to enqueue for user {} — skipping", userId, e);
            }
        }

        log.info("WeeklyReportCron: enqueued {} COMPUTE_WEEKLY_REPORT job(s) for weekStart={}",
                enqueued, weekStartIso);
        return enqueued;
    }
}
