package com.fittribe.api.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Data-access layer for the {@code pending_jobs} queue.
 *
 * <p>All state transitions are expressed as native SQL updates (rather
 * than JPA dirty-checking) so the queue semantics are obvious when
 * reading the repository. The one tricky bit is {@link #findNextClaimable()}
 * — it relies on Postgres' {@code FOR UPDATE SKIP LOCKED} to let many
 * workers race for the next row without deadlocking, and it must be
 * called inside a transaction that also writes the row to
 * {@code running}, otherwise the row lock is dropped before the claim
 * is visible.
 */
@Repository
public interface PendingJobRepository extends JpaRepository<PendingJob, Long> {

    /**
     * Peek at the next claimable row and take an exclusive row-lock on
     * it. Must be called inside a {@code @Transactional} method that
     * immediately updates the row to {@code running} (otherwise the
     * lock is released on commit and another worker grabs the same
     * row). The {@code FOR UPDATE SKIP LOCKED} clause guarantees that
     * concurrent workers pick disjoint rows.
     */
    @Query(value = """
            SELECT * FROM pending_jobs
            WHERE status = 'pending'
              AND scheduled_for <= NOW()
            ORDER BY scheduled_for
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<PendingJob> findNextClaimable();

    /**
     * Mark a claimed row as in-flight. Increments {@code attempts} so
     * that the retry count reflects every time the worker has tried to
     * run the job — a job that succeeds on its first attempt finishes
     * with {@code attempts = 1}.
     */
    @Modifying
    @Query(value = """
            UPDATE pending_jobs
            SET status = 'running',
                started_at = NOW(),
                attempts = attempts + 1
            WHERE id = :id
            """, nativeQuery = true)
    int markRunning(@Param("id") Long id);

    /** Final success transition. Wipes any residual error text. */
    @Modifying
    @Query(value = """
            UPDATE pending_jobs
            SET status = 'completed',
                completed_at = NOW(),
                error = NULL
            WHERE id = :id
            """, nativeQuery = true)
    int markCompleted(@Param("id") Long id);

    /**
     * Recoverable failure — put the row back into {@code pending} with
     * a future {@code scheduled_for} so the worker won't pick it up
     * until the backoff window has elapsed. The caller is responsible
     * for deciding the backoff delay (see
     * {@link JobWorker#backoffMinutes(int)}).
     */
    @Modifying
    @Query(value = """
            UPDATE pending_jobs
            SET status = 'pending',
                scheduled_for = :scheduledFor,
                error = :error,
                started_at = NULL
            WHERE id = :id
            """, nativeQuery = true)
    int markPendingWithBackoff(@Param("id") Long id,
                               @Param("scheduledFor") Instant scheduledFor,
                               @Param("error") String error);

    /**
     * Final failure — the retry budget is exhausted. The row stays in
     * {@code failed} and is never picked up again; an operator has to
     * flip it back to {@code pending} by hand (or an admin endpoint) if
     * they want another attempt.
     */
    @Modifying
    @Query(value = """
            UPDATE pending_jobs
            SET status = 'failed',
                completed_at = NOW(),
                error = :error
            WHERE id = :id
            """, nativeQuery = true)
    int markFailed(@Param("id") Long id, @Param("error") String error);

    /**
     * Rescue jobs that went {@code running} but never completed — this
     * happens when a worker crashes between {@code markRunning} and the
     * terminal state change. Any row that has been {@code running}
     * since before {@code staleBefore} is flipped back to
     * {@code pending} so another worker can pick it up. The attempts
     * counter is preserved so a chronically-crashing job still runs out
     * of retries.
     */
    @Modifying
    @Query(value = """
            UPDATE pending_jobs
            SET status = 'pending',
                scheduled_for = NOW(),
                started_at = NULL,
                error = COALESCE(error, 'recovered from stuck running state')
            WHERE status = 'running'
              AND started_at < :staleBefore
            """, nativeQuery = true)
    int recoverStuck(@Param("staleBefore") Instant staleBefore);
}
