package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.jobs.JobEnqueuer;
import com.fittribe.api.jobs.JobType;
import com.fittribe.api.jobs.JobWorker;
import com.fittribe.api.jobs.WeeklyReportCron;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-only endpoint for manually triggering weekly-report and
 * strength-progression computations (Wynners A1.5 + B-Silent). Two
 * call shapes:
 *
 * <ul>
 *   <li>{@code POST /api/v1/admin/jobs/trigger-weekly-report}
 *       with body {@code {"userId": "<uuid>"}} — enqueues both
 *       {@link JobType#COMPUTE_WEEKLY_REPORT} and
 *       {@link JobType#COMPUTE_STRENGTH_PROGRESSION} for that user,
 *       after verifying the user exists, is active, and has not
 *       requested deletion.</li>
 *   <li>Same endpoint with body {@code {}} (or {@code userId} null) —
 *       delegates to {@link WeeklyReportCron#fanOutSundayJobs()} to
 *       fire both jobs per active user. Same cohort as the Sunday-night
 *       cron.</li>
 * </ul>
 *
 * Both paths stamp the job payloads with {@code weekStart =
 * JobWorker.previousMondayIst()} — the Monday strictly before today
 * in {@code Asia/Kolkata}. An admin retrying a Sunday-night miss on
 * Monday morning will therefore target the same week the cron would
 * have.
 *
 * <h3>Authentication</h3>
 * The endpoint is {@code permitAll()} in
 * {@link com.fittribe.api.config.SecurityConfig} because an admin
 * caller has no user account and therefore no JWT. Access is gated
 * instead by a shared secret sent in the {@code X-Admin-Secret}
 * request header. The secret is sourced from
 * {@code fittribe.admin.secret}, which in turn pulls from the
 * {@code ADMIN_SECRET} environment variable.
 *
 * <p><b>Deny-by-default:</b> if the config resolves to empty or
 * whitespace (e.g. {@code ADMIN_SECRET} unset on Railway), every
 * request is rejected with {@code 401 UNAUTHORIZED} regardless of the
 * header value. That way, forgetting to set {@code ADMIN_SECRET} on a
 * new environment is fail-safe rather than fail-open.
 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobTriggerController {

    private static final Logger log = LoggerFactory.getLogger(AdminJobTriggerController.class);

    private final UserRepository userRepo;
    private final JobEnqueuer jobEnqueuer;
    private final WeeklyReportCron weeklyReportCron;
    private final String configuredSecret;

    public AdminJobTriggerController(UserRepository userRepo,
                                     JobEnqueuer jobEnqueuer,
                                     WeeklyReportCron weeklyReportCron,
                                     @Value("${fittribe.admin.secret:}") String configuredSecret) {
        this.userRepo = userRepo;
        this.jobEnqueuer = jobEnqueuer;
        this.weeklyReportCron = weeklyReportCron;
        this.configuredSecret = configuredSecret;
    }

    /**
     * Manually trigger enqueues for both COMPUTE_WEEKLY_REPORT and
     * COMPUTE_STRENGTH_PROGRESSION — either for a single user (when
     * {@code userId} is present in the body) or for the full active
     * cohort (when it's absent).
     *
     * @return {@code 200 {"enqueued": N}} on success, where N is 2 for
     *         a single-user trigger (both jobs) or 2×(number of active
     *         users) for a cohort trigger.
     * @throws ApiException {@code 401} when the secret is missing,
     *         unset in config, or doesn't match; {@code 400} when
     *         {@code userId} is provided but not a valid UUID;
     *         {@code 404} when {@code userId} is provided but no
     *         matching active, non-deleting user is found.
     */
    @PostMapping("/trigger-weekly-report")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerWeeklyReport(
            @RequestHeader(value = "X-Admin-Secret", required = false) String providedSecret,
            @RequestBody(required = false) TriggerRequest body) {

        requireAdminSecret(providedSecret);

        LocalDate weekStart = JobWorker.previousMondayIst();
        String weekStartIso = weekStart.toString();

        int enqueued;
        String rawUserId = body == null ? null : body.userId;

        if (rawUserId == null || rawUserId.isBlank()) {
            // All-active-users path — reuse the same fan-out helper the
            // Sunday cron calls so there's no logic drift between the
            // two triggers.
            log.info("AdminJobTrigger: fan-out requested (no userId in body), weekStart={}", weekStartIso);
            enqueued = weeklyReportCron.fanOutSundayJobs();
        } else {
            // Single-user path — enqueue both job types.
            UUID userId = parseUserId(rawUserId);
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> ApiException.notFound("User"));
            if (!Boolean.TRUE.equals(user.getIsActive())
                    || user.getDeletionRequestedAt() != null) {
                // Treat "inactive / deleting" the same as "not found"
                // so an admin can't use this endpoint as a probe for
                // the account-status column.
                throw ApiException.notFound("User");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId.toString());
            payload.put("weekStart", weekStartIso);

            int enqueuedJobs = 0;

            // Enqueue weekly report
            try {
                jobEnqueuer.enqueue(JobType.COMPUTE_WEEKLY_REPORT, payload);
                enqueuedJobs++;
            } catch (Exception e) {
                log.error("AdminJobTrigger: failed to enqueue COMPUTE_WEEKLY_REPORT for user {} — continuing",
                        userId, e);
            }

            // Enqueue strength progression
            try {
                jobEnqueuer.enqueue(JobType.COMPUTE_STRENGTH_PROGRESSION, payload);
                enqueuedJobs++;
            } catch (Exception e) {
                log.error("AdminJobTrigger: failed to enqueue COMPUTE_STRENGTH_PROGRESSION for user {} — continuing",
                        userId, e);
            }

            enqueued = enqueuedJobs;
            log.info("AdminJobTrigger: single-user enqueue userId={} weekStart={} enqueued={} jobs",
                    userId, weekStartIso, enqueuedJobs);
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("enqueued", enqueued)));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Validate the shared secret. Deny-by-default: empty/missing
     * config rejects every request; a non-empty configured value must
     * match the header exactly (constant-time compare to avoid timing
     * side-channels, though the surface is small enough that it's
     * mostly hygienic).
     */
    private void requireAdminSecret(String provided) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("AdminJobTrigger: request rejected — fittribe.admin.secret is not configured");
            throw ApiException.unauthorized();
        }
        if (provided == null || provided.isEmpty()) {
            throw ApiException.unauthorized();
        }
        if (!constantTimeEquals(configuredSecret, provided)) {
            throw ApiException.unauthorized();
        }
    }

    /**
     * Parse a caller-provided userId string. Any malformed UUID is a
     * {@code 400 VALIDATION_ERROR} — the caller can fix it and retry.
     */
    private static UUID parseUserId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "userId is not a valid UUID.");
        }
    }

    /**
     * Constant-time string comparison. Avoids leaking information
     * about the configured secret via timing differences between
     * "first byte wrong" and "last byte wrong" — standard practice
     * even when the exposure is small.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    /**
     * Request body for {@code POST /admin/jobs/trigger-weekly-report}.
     * {@code userId} is optional: absent means "fan out to all active
     * users" via {@link WeeklyReportCron#fanOutSundayJobs()} to enqueue
     * both COMPUTE_WEEKLY_REPORT and COMPUTE_STRENGTH_PROGRESSION.
     */
    public static class TriggerRequest {
        public String userId;

        public TriggerRequest() {}

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
}
