package com.fittribe.api.scheduler;

import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Nightly cleanup of orphaned, never-onboarded users.
 *
 * <p>The {@code users} row is INSERTed at Firebase OTP verification, before
 * onboarding data exists. A user who verifies their phone and kills the app
 * before the final onboarding submit leaves a row with NULL name/gender/
 * goal/fitness_level/height/weight and {@code onboarding_complete = false}
 * forever. This job deletes those rows once they are older than 7 days.
 *
 * <p>Runs at 03:00 IST daily. Candidate lookup is backed by the partial
 * index {@code idx_users_onboarding_complete_created_at} (V68).
 *
 * <h3>FK handling</h3>
 * Almost every table that references {@code users(id)} is declared
 * {@code ON DELETE CASCADE} (workout_sessions, pr_events,
 * user_session_feedback, set_logs via workout_sessions, notifications,
 * coin_transactions, etc.) so a single {@code DELETE FROM users} cascades
 * cleanly. The one exception that would block the delete is
 * {@code user_plans.user_id REFERENCES users(id)} (no ON DELETE clause,
 * see V8) — those rows are deleted explicitly first. ({@code groups.created_by}
 * is also non-cascade, but a never-onboarded user cannot have created a
 * group — group creation is a post-onboarding action — so it is not a
 * reachable FK for this cohort.)
 */
@Component
public class IncompleteUserCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncompleteUserCleanupScheduler.class);

    /** A row must be at least this old before it is eligible for deletion. */
    private static final int STALE_AFTER_DAYS = 7;

    private final UserRepository userRepo;
    private final JdbcTemplate   jdbc;

    public IncompleteUserCleanupScheduler(UserRepository userRepo, JdbcTemplate jdbc) {
        this.userRepo = userRepo;
        this.jdbc     = jdbc;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupIncompleteUsers() {
        Instant cutoff = Instant.now().minus(STALE_AFTER_DAYS, ChronoUnit.DAYS);
        List<UUID> ids = userRepo.findIncompleteUserIdsCreatedBefore(cutoff);

        if (ids.isEmpty()) {
            log.info("IncompleteUserCleanupScheduler: incomplete_user_cleanup.deleted_count=0 (none stale)");
            return;
        }

        // Delete the one non-cascade dependency first, then the user rows.
        // All other users-referencing FKs are ON DELETE CASCADE and are
        // resolved by Postgres when the users rows are removed.
        int plansDeleted = jdbc.update(
                "DELETE FROM user_plans WHERE user_id = ANY(?)",
                (Object) ids.toArray(new UUID[0]));

        int deleted = jdbc.update(
                "DELETE FROM users WHERE id = ANY(?)",
                (Object) ids.toArray(new UUID[0]));

        log.info("IncompleteUserCleanupScheduler: incomplete_user_cleanup.deleted_count={} "
                        + "(user_plans rows removed={}, cutoff={})",
                deleted, plansDeleted, cutoff);
    }
}
