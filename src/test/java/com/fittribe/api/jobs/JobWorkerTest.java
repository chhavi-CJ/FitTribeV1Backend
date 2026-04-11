package com.fittribe.api.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.weeklyreport.WeeklyReportComputer;
import com.fittribe.api.strengthscore.ProgressSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JobWorker}. The repository is mocked, so the
 * tests focus on the state-machine semantics: claim → dispatch →
 * terminal write, retry-with-backoff on recoverable failures, and
 * final-failure after the retry budget is spent.
 *
 * <h3>Transaction manager</h3>
 * {@link PlatformTransactionManager} is mocked to return a no-op
 * {@link TransactionStatus}, which makes Spring's
 * {@code TransactionTemplate} execute the lambda synchronously on the
 * calling thread — exactly what a unit test wants.
 */
class JobWorkerTest {

    private PendingJobRepository repo;
    private PlatformTransactionManager txManager;
    private FakeWeeklyReportComputer computer;
    private FakeProgressSnapshotService snapshotService;
    private JobWorker worker;

    @BeforeEach
    void setUp() {
        repo = mock(PendingJobRepository.class);
        txManager = mock(PlatformTransactionManager.class);
        // Mockito on JDK 25 can't instrument WeeklyReportComputer (same
        // class-modification limitation that bit ObjectMapper and
        // TransactionStatus in Stage 2), so we roll a concrete fake that
        // records invocations and can optionally throw on demand.
        computer = new FakeWeeklyReportComputer();
        snapshotService = new FakeProgressSnapshotService();
        // SimpleTransactionStatus is a concrete no-op implementation —
        // Mockito on JDK 25 can't proxy the TransactionStatus interface
        // chain (which includes Flushable), so we return a real stub.
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker = new JobWorker(repo, new ObjectMapper(), txManager, computer, snapshotService);
    }

    // ── Empty queue ──────────────────────────────────────────────────────

    @Test
    void tickWithEmptyQueueIsNoOp() {
        when(repo.findNextClaimable()).thenReturn(Optional.empty());

        worker.tick();

        verify(repo).findNextClaimable();
        verify(repo, never()).markRunning(anyLong());
        verify(repo, never()).markCompleted(anyLong());
    }

    // ── Happy path ───────────────────────────────────────────────────────

    @Test
    void tickDispatchesAndMarksCompletedOnSuccess() {
        PendingJob job = pending(1L, "COMPUTE_WEEKLY_REPORT",
                "{\"userId\":\"aaaaaaaa-0000-0000-0000-000000000000\",\"weekStart\":\"2026-04-06\"}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        worker.tick();

        verify(repo).markRunning(1L);
        verify(repo).markCompleted(1L);
        verify(repo, never()).markPendingWithBackoff(anyLong(), any(), anyString());
        verify(repo, never()).markFailed(anyLong(), anyString());
        // Computer should have been invoked with the parsed payload
        assertEquals(1, computer.calls.size());
        assertEquals(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
                computer.calls.get(0).userId);
        assertEquals(LocalDate.of(2026, 4, 6), computer.calls.get(0).weekStart);
    }

    // ── Missing weekStart defaults to previous IST Monday ───────────────

    @Test
    void tickWithMissingWeekStartFallsBackToPreviousMondayIst() {
        PendingJob job = pending(2L, "COMPUTE_WEEKLY_REPORT",
                "{\"userId\":\"bbbbbbbb-0000-0000-0000-000000000000\"}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        worker.tick();

        verify(repo).markCompleted(2L);
        assertEquals(1, computer.calls.size());
        assertEquals(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000"),
                computer.calls.get(0).userId);
        assertEquals(JobWorker.previousMondayIst(), computer.calls.get(0).weekStart);
    }

    // ── Computer throwing triggers retry path ───────────────────────────

    @Test
    void computerFailureTriggersBackoff() {
        PendingJob job = pending(3L, "COMPUTE_WEEKLY_REPORT",
                "{\"userId\":\"cccccccc-0000-0000-0000-000000000000\",\"weekStart\":\"2026-04-06\"}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));
        computer.throwOnCompute = new RuntimeException("boom");

        worker.tick();

        verify(repo).markRunning(3L);
        verify(repo, never()).markCompleted(anyLong());
        verify(repo).markPendingWithBackoff(eq(3L), any(), anyString());
        assertEquals(1, computer.calls.size());
    }

    // ── Malformed payload (missing userId) fails deterministically ──────

    @Test
    void missingUserIdFailsWithoutCallingComputer() {
        PendingJob job = pending(4L, "COMPUTE_WEEKLY_REPORT", "{}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        worker.tick();

        verify(repo).markRunning(4L);
        verify(repo, never()).markCompleted(anyLong());
        verify(repo).markPendingWithBackoff(eq(4L), any(), anyString());
        assertTrue(computer.calls.isEmpty(), "computer must not be called when userId is missing");
    }

    // ── Recoverable failure → backoff ────────────────────────────────────

    @Test
    void dispatchFailureOnFirstAttemptReschedulesWithExponentialBackoff() {
        // attempts=0 in DB → claim increments to 1 → first failure →
        // 2^1 = 2 minutes of backoff.
        PendingJob job = pending(7L, "UNKNOWN_TYPE_TRIGGERS_FAILURE", "{}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        Instant before = Instant.now();
        worker.tick();
        Instant after = Instant.now();

        verify(repo).markRunning(7L);
        verify(repo, never()).markCompleted(anyLong());
        verify(repo, never()).markFailed(anyLong(), anyString());

        ArgumentCaptor<Instant> whenCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<String> errCaptor  = ArgumentCaptor.forClass(String.class);
        verify(repo).markPendingWithBackoff(eq(7L), whenCaptor.capture(), errCaptor.capture());

        Instant scheduledFor = whenCaptor.getValue();
        // Expect ~2 minutes from "now" — allow 10s of slack either way
        // to absorb scheduler jitter and the assertion clock drift.
        assertTrue(scheduledFor.isAfter(before.plusSeconds(110)),
                "backoff should be ≥ 110s, was " + java.time.Duration.between(before, scheduledFor));
        assertTrue(scheduledFor.isBefore(after.plusSeconds(130)),
                "backoff should be ≤ 130s, was " + java.time.Duration.between(after, scheduledFor));

        assertTrue(errCaptor.getValue().contains("UNKNOWN_TYPE_TRIGGERS_FAILURE"),
                "error text should mention the unknown job type");
    }

    // ── Max attempts reached → permanently failed ────────────────────────

    @Test
    void dispatchFailureOnLastAttemptMarksJobPermanentlyFailed() {
        // attempts=4 in DB → claim increments to 5 → >= MAX_ATTEMPTS → markFailed
        PendingJob job = pending(9L, "UNKNOWN_TYPE", "{}", 4);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        worker.tick();

        verify(repo).markRunning(9L);
        verify(repo).markFailed(eq(9L), anyString());
        verify(repo, never()).markCompleted(anyLong());
        verify(repo, never()).markPendingWithBackoff(anyLong(), any(), anyString());
    }

    // ── Claim-time failure doesn't crash the scheduler thread ────────────

    @Test
    void claimFailureIsLoggedAndSwallowed() {
        when(repo.findNextClaimable()).thenThrow(new RuntimeException("db down"));

        // Must not throw — the scheduler thread must survive.
        worker.tick();

        verify(repo).findNextClaimable();
        verify(repo, never()).markRunning(anyLong());
        verify(repo, never()).markCompleted(anyLong());
    }

    // ── Stuck-job recovery ───────────────────────────────────────────────

    @Test
    void recoverStuckJobsCallsRepoWithPastCutoff() {
        when(repo.recoverStuck(any())).thenReturn(3);

        Instant before = Instant.now();
        worker.recoverStuckJobs();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repo).recoverStuck(cutoff.capture());
        // Cutoff should be ~2 minutes before "now".
        Instant c = cutoff.getValue();
        assertTrue(c.isBefore(before.minusSeconds(110)),
                "cutoff should be ≥ 110s old, was " + java.time.Duration.between(c, before));
        assertTrue(c.isAfter(before.minusSeconds(130)),
                "cutoff should be ≤ 130s old, was " + java.time.Duration.between(c, before));
        verifyNoMoreInteractions(repo);
    }

    @Test
    void recoverStuckJobsSwallowsExceptions() {
        when(repo.recoverStuck(any())).thenThrow(new RuntimeException("db down"));
        worker.recoverStuckJobs(); // must not throw
        verify(repo).recoverStuck(any());
    }

    // ── Backoff curve ────────────────────────────────────────────────────

    @Test
    void backoffCurveIsExponential() {
        assertEquals(2L,  JobWorker.backoffMinutes(1));
        assertEquals(4L,  JobWorker.backoffMinutes(2));
        assertEquals(8L,  JobWorker.backoffMinutes(3));
        assertEquals(16L, JobWorker.backoffMinutes(4));
    }

    // ── COMPUTE_STRENGTH_PROGRESSION dispatch ────────────────────────────

    @Test
    void tickDispatchesComputeStrengthProgressionAndMarksCompletedOnSuccess() {
        PendingJob job = pending(10L, "COMPUTE_STRENGTH_PROGRESSION",
                "{\"userId\":\"dddddddd-0000-0000-0000-000000000000\",\"weekStart\":\"2026-04-06\"}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        worker.tick();

        verify(repo).markRunning(10L);
        verify(repo).markCompleted(10L);
        verify(repo, never()).markPendingWithBackoff(anyLong(), any(), anyString());
        verify(repo, never()).markFailed(anyLong(), anyString());
        // Snapshot service should have been invoked with the parsed payload
        assertEquals(1, snapshotService.calls.size());
        assertEquals(UUID.fromString("dddddddd-0000-0000-0000-000000000000"),
                snapshotService.calls.get(0).userId);
        assertEquals(LocalDate.of(2026, 4, 6), snapshotService.calls.get(0).weekStart);
    }

    @Test
    void computeStrengthProgressionWithMissingWeekStartFallsBackToPreviousMondayIst() {
        PendingJob job = pending(11L, "COMPUTE_STRENGTH_PROGRESSION",
                "{\"userId\":\"eeeeeeee-0000-0000-0000-000000000000\"}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));

        worker.tick();

        verify(repo).markCompleted(11L);
        assertEquals(1, snapshotService.calls.size());
        assertEquals(UUID.fromString("eeeeeeee-0000-0000-0000-000000000000"),
                snapshotService.calls.get(0).userId);
        assertEquals(JobWorker.previousMondayIst(), snapshotService.calls.get(0).weekStart);
    }

    @Test
    void computeStrengthProgressionFailureTriggersBackoff() {
        PendingJob job = pending(12L, "COMPUTE_STRENGTH_PROGRESSION",
                "{\"userId\":\"ffffffff-0000-0000-0000-000000000000\",\"weekStart\":\"2026-04-06\"}", 0);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));
        snapshotService.throwOnCompute = new RuntimeException("snapshot compute failed");

        worker.tick();

        verify(repo).markRunning(12L);
        verify(repo, never()).markCompleted(anyLong());
        verify(repo).markPendingWithBackoff(eq(12L), any(), anyString());
        assertEquals(1, snapshotService.calls.size());
    }

    @Test
    void computeStrengthProgressionFailureOnMaxAttemptsMarksPermanentlyFailed() {
        // attempts=4 in DB → claim increments to 5 → >= MAX_ATTEMPTS → markFailed
        PendingJob job = pending(13L, "COMPUTE_STRENGTH_PROGRESSION",
                "{\"userId\":\"abcdefab-0000-0000-0000-000000000000\",\"weekStart\":\"2026-04-06\"}", 4);
        when(repo.findNextClaimable()).thenReturn(Optional.of(job));
        snapshotService.throwOnCompute = new RuntimeException("permanent failure");

        worker.tick();

        verify(repo).markRunning(13L);
        verify(repo).markFailed(eq(13L), anyString());
        verify(repo, never()).markCompleted(anyLong());
        verify(repo, never()).markPendingWithBackoff(anyLong(), any(), anyString());
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private static PendingJob pending(long id, String type, String payload, int attempts) {
        PendingJob j = new PendingJob();
        j.setId(id);
        j.setJobType(type);
        j.setPayload(payload);
        j.setStatus("pending");
        j.setAttempts(attempts);
        j.setCreatedAt(Instant.now());
        j.setScheduledFor(Instant.now());
        return j;
    }

    /**
     * Hand-rolled stand-in for {@link WeeklyReportComputer}. The real
     * class cannot be mocked on JDK 25 — Mockito's inline mock maker
     * can't re-transform it — and we don't want to depend on every
     * downstream bean in a unit test. The constructor passes nulls to
     * super because the real constructor only assigns fields; no
     * validation fires until {@code compute()} is called, which we
     * override.
     */
    static final class FakeWeeklyReportComputer extends WeeklyReportComputer {
        final List<Call> calls = new ArrayList<>();
        RuntimeException throwOnCompute;

        FakeWeeklyReportComputer() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public void compute(UUID userId, LocalDate weekStart) {
            calls.add(new Call(userId, weekStart));
            if (throwOnCompute != null) {
                throw throwOnCompute;
            }
        }

        static final class Call {
            final UUID userId;
            final LocalDate weekStart;
            Call(UUID userId, LocalDate weekStart) {
                this.userId = userId;
                this.weekStart = weekStart;
            }
        }
    }

    /**
     * Hand-rolled stand-in for {@link ProgressSnapshotService}. Records
     * invocations and can optionally throw to simulate compute failures.
     */
    static final class FakeProgressSnapshotService extends ProgressSnapshotService {
        final List<Call> calls = new ArrayList<>();
        RuntimeException throwOnCompute;

        FakeProgressSnapshotService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public void computeForUserWeek(UUID userId, LocalDate weekStart) {
            calls.add(new Call(userId, weekStart));
            if (throwOnCompute != null) {
                throw throwOnCompute;
            }
        }

        static final class Call {
            final UUID userId;
            final LocalDate weekStart;
            Call(UUID userId, LocalDate weekStart) {
                this.userId = userId;
                this.weekStart = weekStart;
            }
        }
    }
}
