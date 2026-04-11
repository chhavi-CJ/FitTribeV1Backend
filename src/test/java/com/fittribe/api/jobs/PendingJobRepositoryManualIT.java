package com.fittribe.api.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual integration test — exercises the {@link PendingJobRepository}
 * native queries against the live Railway Postgres database.
 *
 * <p>Gated by {@code -Dfittribe.manualTest=true} so it never runs in
 * normal {@code mvn test}. Matches the pattern used by
 * {@link com.fittribe.api.findings.WeekDataBuilderManualIT}.
 *
 * <h3>Why manual rather than @DataJpaTest</h3>
 * The repository leans on Postgres-only features — {@code JSONB} for
 * the payload column and {@code FOR UPDATE SKIP LOCKED} for the claim
 * query — neither of which H2 models correctly. Adding Testcontainers
 * would give better fidelity but requires Docker on every
 * {@code mvn test} run; we follow the existing manual-IT pattern until
 * the codebase adopts Testcontainers for anything else.
 *
 * <h3>How to run</h3>
 * <pre>
 * export $(cat .env | xargs)
 * mvn test \
 *   -Dtest=PendingJobRepositoryManualIT \
 *   -Dfittribe.manualTest=true \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Each test inserts its own rows, verifies behaviour, and rolls
 * back via {@code @Transactional}, so the {@code pending_jobs} table
 * is clean after every run.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "fittribe.manualTest", matches = "true")
@Transactional
class PendingJobRepositoryManualIT {

    @Autowired
    private PendingJobRepository repo;

    @Test
    void claimPicksEarliestPendingRowAndTransitionsItToRunning() {
        // Arrange — insert two pending jobs, the second scheduled earlier
        // to prove we pick by scheduled_for not by insertion order.
        Instant now = Instant.now();
        PendingJob laterJob = newPending("COMPUTE_WEEKLY_REPORT",
                "{\"userId\":\"aaaaaaaa-0000-0000-0000-000000000000\"}",
                now.plusSeconds(5));
        PendingJob earlierJob = newPending("COMPUTE_WEEKLY_REPORT",
                "{\"userId\":\"bbbbbbbb-0000-0000-0000-000000000000\"}",
                now.minusSeconds(5));
        repo.save(laterJob);
        repo.save(earlierJob);

        // Act
        Optional<PendingJob> claimed = repo.findNextClaimable();

        // Assert — earlier job wins
        assertTrue(claimed.isPresent(), "expected a claimable row");
        assertEquals(earlierJob.getId(), claimed.get().getId());
        assertEquals("pending", claimed.get().getStatus());

        // Transition it and verify the row is no longer claimable.
        int updated = repo.markRunning(claimed.get().getId());
        assertEquals(1, updated);

        PendingJob reloaded = repo.findById(claimed.get().getId()).orElseThrow();
        assertEquals("running", reloaded.getStatus());
        assertNotNull(reloaded.getStartedAt());
        assertEquals(1, reloaded.getAttempts());
    }

    @Test
    void futureScheduledRowsAreNotClaimable() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        repo.save(newPending("COMPUTE_WEEKLY_REPORT", "{}", future));

        // The only row is scheduled an hour in the future — either the
        // queue is empty (no other pending rows) or we get a row other
        // than ours. Either way, our future row must never appear.
        Optional<PendingJob> claimed = repo.findNextClaimable();
        claimed.ifPresent(j -> assertFalse(j.getScheduledFor().isAfter(Instant.now()),
                "claim should never return a row scheduled in the future"));
    }

    @Test
    void markCompletedTransitionsRowAndClearsError() {
        PendingJob job = repo.save(newPending("COMPUTE_WEEKLY_REPORT", "{}", Instant.now()));
        repo.markRunning(job.getId());

        int updated = repo.markCompleted(job.getId());
        assertEquals(1, updated);

        PendingJob reloaded = repo.findById(job.getId()).orElseThrow();
        assertEquals("completed", reloaded.getStatus());
        assertNotNull(reloaded.getCompletedAt());
        assertNull(reloaded.getError());
    }

    @Test
    void markPendingWithBackoffReschedulesRowForLater() {
        PendingJob job = repo.save(newPending("COMPUTE_WEEKLY_REPORT", "{}", Instant.now()));
        repo.markRunning(job.getId());

        Instant nextRun = Instant.now().plus(2, ChronoUnit.MINUTES);
        int updated = repo.markPendingWithBackoff(job.getId(), nextRun, "boom");
        assertEquals(1, updated);

        PendingJob reloaded = repo.findById(job.getId()).orElseThrow();
        assertEquals("pending", reloaded.getStatus());
        assertEquals("boom", reloaded.getError());
        assertNull(reloaded.getStartedAt());
        // Row must not be claimable yet
        assertTrue(reloaded.getScheduledFor().isAfter(Instant.now().plusSeconds(60)),
                "rescheduled row must be at least ~60s in the future");
    }

    @Test
    void markFailedSetsTerminalState() {
        PendingJob job = repo.save(newPending("COMPUTE_WEEKLY_REPORT", "{}", Instant.now()));
        repo.markRunning(job.getId());

        int updated = repo.markFailed(job.getId(), "exhausted retries");
        assertEquals(1, updated);

        PendingJob reloaded = repo.findById(job.getId()).orElseThrow();
        assertEquals("failed", reloaded.getStatus());
        assertEquals("exhausted retries", reloaded.getError());
        assertNotNull(reloaded.getCompletedAt());
    }

    @Test
    void recoverStuckResetsOldRunningRows() {
        // Insert a row, mark it running, then recover anything older
        // than "now". The row's started_at is set by markRunning via
        // NOW() — so to make it look stale we bump staleBefore to the
        // far future rather than rewriting the started_at column.
        PendingJob job = repo.save(newPending("COMPUTE_WEEKLY_REPORT", "{}", Instant.now()));
        repo.markRunning(job.getId());

        Instant farFuture = Instant.now().plus(1, ChronoUnit.HOURS);
        int recovered = repo.recoverStuck(farFuture);
        assertTrue(recovered >= 1, "expected at least this row to be recovered");

        PendingJob reloaded = repo.findById(job.getId()).orElseThrow();
        assertEquals("pending", reloaded.getStatus());
        assertNull(reloaded.getStartedAt());
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private static PendingJob newPending(String type, String payload, Instant scheduledFor) {
        PendingJob j = new PendingJob();
        j.setJobType(type);
        j.setPayload(payload);
        j.setStatus("pending");
        j.setAttempts(0);
        j.setCreatedAt(Instant.now());
        j.setScheduledFor(scheduledFor);
        return j;
    }
}
