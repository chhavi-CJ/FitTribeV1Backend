package com.fittribe.api.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.weeklyreport.WeeklyReportComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;

/**
 * Polling worker that drains the {@code pending_jobs} queue.
 *
 * <p>Runs on a Spring {@link Scheduled} tick every 10 seconds. Each tick
 * claims a single row (via {@code FOR UPDATE SKIP LOCKED}, so multiple
 * workers can run side-by-side without fighting), dispatches it to a
 * handler, and persists the outcome. A second schedule runs every 5
 * minutes to recover jobs that were claimed by a crashed worker and
 * left stranded in {@code running}.
 *
 * <h3>Transaction model</h3>
 * The claim must be in its own short transaction so the row lock is
 * released as soon as {@code status='running'} is committed — only then
 * are concurrent workers guaranteed to skip the row. Dispatch then runs
 * outside any transaction (it can take up to 30s for weekly reports),
 * and the terminal state change (completed / failed / pending-with-
 * backoff) runs in its own short transaction. {@link TransactionTemplate}
 * is used instead of {@code @Transactional} on private methods to avoid
 * Spring's self-invocation footgun.
 *
 * <h3>Retry policy</h3>
 * Exponential backoff: {@code 2^attempts} minutes. Max {@link #MAX_ATTEMPTS}
 * attempts before a job is marked {@code failed} permanently. Because
 * {@link PendingJobRepository#markRunning} increments attempts up-front,
 * a first-try failure is recorded as {@code attempts=1} with a 2-minute
 * backoff; the fifth failure writes {@code failed}.
 *
 * <h3>Dispatch</h3>
 * {@link JobType#COMPUTE_WEEKLY_REPORT} delegates to
 * {@link WeeklyReportComputer}. The payload MUST carry a {@code userId}
 * (UUID); {@code weekStart} is optional and defaults to
 * {@link #previousMondayIst()} — the most recent Monday strictly
 * before today in {@code Asia/Kolkata}. That fallback is a dev/admin
 * safety net — production enqueues from the Sunday cron and the admin
 * endpoint always pass {@code weekStart} explicitly.
 */
@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    /** Max number of dispatch attempts before a job is permanently failed. */
    static final int MAX_ATTEMPTS = 5;

    /**
     * How long a row may sit in {@code running} before it is considered
     * abandoned and reclaimed by {@link #recoverStuckJobs()}.
     */
    static final Duration STUCK_THRESHOLD = Duration.ofMinutes(2);

    /** Zone used for the {@code weekStart} fallback. Matches the cron trigger zone. */
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PendingJobRepository repo;
    private final ObjectMapper mapper;
    private final TransactionTemplate txTemplate;
    private final WeeklyReportComputer weeklyReportComputer;

    public JobWorker(PendingJobRepository repo,
                     ObjectMapper mapper,
                     PlatformTransactionManager txManager,
                     WeeklyReportComputer weeklyReportComputer) {
        this.repo = repo;
        this.mapper = mapper;
        this.txTemplate = new TransactionTemplate(txManager);
        this.weeklyReportComputer = weeklyReportComputer;
    }

    /**
     * Drain one job per tick. Runs every 10 seconds. Missing or empty
     * tick is a no-op — the next tick will try again.
     */
    @Scheduled(fixedDelay = 10_000L)
    public void tick() {
        PendingJob job;
        try {
            job = claimNext();
        } catch (Exception e) {
            // A claim failure (DB down, connection issue) should not
            // crash the scheduler thread — just log and try again next
            // tick.
            log.error("JobWorker: claim failed, will retry on next tick", e);
            return;
        }
        if (job == null) {
            return;
        }

        log.info("JobWorker: dispatching job id={} type={} attempt={}",
                job.getId(), job.getJobType(), job.getAttempts());
        try {
            dispatch(job);
            txTemplate.executeWithoutResult(s -> repo.markCompleted(job.getId()));
            log.info("JobWorker: job id={} completed", job.getId());
        } catch (Exception e) {
            handleFailure(job, e);
        }
    }

    /**
     * Recover jobs that were picked up by a worker that has since died.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000L)
    public void recoverStuckJobs() {
        Instant staleBefore = Instant.now().minus(STUCK_THRESHOLD);
        int recovered;
        try {
            recovered = txTemplate.execute(s -> repo.recoverStuck(staleBefore));
        } catch (Exception e) {
            log.error("JobWorker: stuck-job recovery sweep failed", e);
            return;
        }
        if (recovered > 0) {
            log.warn("JobWorker: recovered {} stuck job(s) older than {}",
                    recovered, staleBefore);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Acquire the next pending row and mark it {@code running} in a
     * single short transaction. Returns {@code null} when the queue is
     * empty.
     */
    private PendingJob claimNext() {
        return txTemplate.execute(status ->
                repo.findNextClaimable().map(job -> {
                    repo.markRunning(job.getId());
                    // Reflect the attempts bump in the in-memory copy
                    // so handleFailure() sees the post-increment value.
                    job.setAttempts(job.getAttempts() + 1);
                    job.setStatus("running");
                    job.setStartedAt(Instant.now());
                    return job;
                }).orElse(null));
    }

    /**
     * Route the job to its handler. Throws on any failure so
     * {@link #tick()} can record it. Adding a new job type means adding
     * a branch here.
     */
    private void dispatch(PendingJob job) {
        String type = job.getJobType();
        if (JobType.COMPUTE_WEEKLY_REPORT.name().equals(type)) {
            handleComputeWeeklyReport(job);
            return;
        }
        // Unknown job types are a programming error — fail fast so the
        // operator notices rather than silently looping.
        throw new IllegalStateException(
                "JobWorker: no handler for job type '" + type + "'");
    }

    /**
     * Parse the payload, resolve {@code userId} (required) and
     * {@code weekStart} (optional, falls back to the previous IST
     * Monday), then delegate to {@link WeeklyReportComputer}. Any
     * failure — bad payload, missing user id, computer exception —
     * bubbles up to {@link #tick()} and hits the retry path.
     */
    private void handleComputeWeeklyReport(PendingJob job) {
        Map<String, Object> payload = parsePayload(job);

        Object rawUserId = payload.get("userId");
        if (rawUserId == null) {
            throw new IllegalArgumentException(
                    "JobWorker: COMPUTE_WEEKLY_REPORT payload missing required 'userId' (job id=" + job.getId() + ")");
        }
        UUID userId;
        try {
            userId = UUID.fromString(rawUserId.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "JobWorker: COMPUTE_WEEKLY_REPORT 'userId' is not a valid UUID (job id=" + job.getId() + ")", e);
        }

        LocalDate weekStart;
        Object rawWeekStart = payload.get("weekStart");
        if (rawWeekStart == null) {
            weekStart = previousMondayIst();
            log.info("JobWorker: COMPUTE_WEEKLY_REPORT job id={} missing 'weekStart' — defaulting to {} (previous IST Monday)",
                    job.getId(), weekStart);
        } else {
            try {
                weekStart = LocalDate.parse(rawWeekStart.toString());
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "JobWorker: COMPUTE_WEEKLY_REPORT 'weekStart' is not ISO-8601 date (job id=" + job.getId() + ")", e);
            }
        }

        weeklyReportComputer.compute(userId, weekStart);
    }

    /**
     * The Monday strictly before today in {@code Asia/Kolkata}. Used
     * as the default {@code weekStart} when a
     * {@link JobType#COMPUTE_WEEKLY_REPORT} payload omits it — a safety
     * net for ad-hoc admin triggers. Production enqueues from the
     * Sunday cron always pass {@code weekStart} explicitly, and on
     * Sunday night IST this fallback resolves to the Monday of the
     * week just ending (6 days ago), which is the correct window.
     *
     * <p>Public because it's also the shared weekStart source for
     * {@link WeeklyReportCron#fanOutActiveUsers()} and
     * {@code AdminJobTriggerController} — both live outside this
     * package. A dedicated utility class would be overkill for a
     * three-line method with three callers.
     */
    public static LocalDate previousMondayIst() {
        return LocalDate.now(IST).with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
    }

    private Map<String, Object> parsePayload(PendingJob job) {
        String raw = job.getPayload();
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return mapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "JobWorker: unreadable payload on job id=" + job.getId(), e);
        }
    }

    private void handleFailure(PendingJob job, Exception cause) {
        String message = truncate(cause.toString(), 2000);
        int attempts = job.getAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            log.error("JobWorker: job id={} permanently failed after {} attempts",
                    job.getId(), attempts, cause);
            txTemplate.executeWithoutResult(s ->
                    repo.markFailed(job.getId(), message));
            return;
        }
        Instant nextRun = Instant.now().plus(backoffMinutes(attempts), java.time.temporal.ChronoUnit.MINUTES);
        log.warn("JobWorker: job id={} failed on attempt {}, retrying at {} ({} min backoff)",
                job.getId(), attempts, nextRun, backoffMinutes(attempts), cause);
        txTemplate.executeWithoutResult(s ->
                repo.markPendingWithBackoff(job.getId(), nextRun, message));
    }

    /**
     * Exponential backoff delay in minutes. {@code attempts=1} → 2 min,
     * {@code attempts=2} → 4 min, {@code attempts=3} → 8 min, etc.
     * Package-private so unit tests can exercise the curve directly.
     */
    static long backoffMinutes(int attempts) {
        // Guard against pathological values; we never call this with
        // attempts < 1 in production but a test mutant might.
        int safe = Math.max(1, attempts);
        return (long) Math.pow(2, safe);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
