package com.fittribe.api.prv2.service;

import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thin async wrapper around {@link PrEditCascadeService} for the edit-set,
 * delete-set, and delete-exercise endpoints. The wrapper exists ONLY to
 * satisfy Spring's @Async proxy boundary — calls into a different bean's
 * @Async method get intercepted; calls into your own bean (this.) do not.
 *
 * <p>SessionController calls these wrapper methods after committing the
 * synchronous JSONB write, so the cascade runs on the prDetectionExecutor
 * pool while the HTTP response is already on its way to the client.
 *
 * <p>Each method's {@code finally} block calls
 * {@code sessionRepo.markPrDetectionComplete(sessionId, Instant.now())},
 * mirroring the finish-flow pattern. This runs even on cascade failure so
 * the frontend's {@code prDetectionComplete} flag eventually flips back
 * to true and the optimistic JSONB.isPr stops being trusted.
 *
 * <p>No timeout enforcement (per design decision). If a cascade hangs,
 * the prDetectionCompletedAt column stays NULL until follow-up cleanup.
 */
@Service
public class PrEditCascadeAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(PrEditCascadeAsyncRunner.class);

    private final PrEditCascadeService cascade;
    private final WorkoutSessionRepository sessionRepo;

    public PrEditCascadeAsyncRunner(PrEditCascadeService cascade,
                                     WorkoutSessionRepository sessionRepo) {
        this.cascade = cascade;
        this.sessionRepo = sessionRepo;
    }

    /**
     * Run the edit cascade off the HTTP thread. Always marks the session's
     * prDetectionCompletedAt at the end (success or failure) so the client
     * stops trusting the optimistic JSONB.isPr.
     */
    @Async("prDetectionExecutor")
    public void runEditCascadeAsync(UUID userId, UUID sessionId, UUID setId,
                                     LoggedSet oldValue, LoggedSet newValue) {
        long t0 = System.nanoTime();
        log.info("editCascade START sessionId={} setId={} thread={}",
                sessionId, setId, Thread.currentThread().getName());
        try {
            cascade.processSetEdit(userId, sessionId, setId, oldValue, newValue);
        } catch (Exception e) {
            log.error("editCascade FAILED sessionId={} setId={}", sessionId, setId, e);
        } finally {
            markCompleteSafely(sessionId);
            log.info("editCascade DONE sessionId={} setId={} totalMs={}",
                    sessionId, setId, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    /**
     * Run the delete-set cascade off the HTTP thread.
     */
    @Async("prDetectionExecutor")
    public void runDeleteCascadeAsync(UUID userId, UUID sessionId, UUID setId,
                                       LoggedSet oldValue) {
        long t0 = System.nanoTime();
        log.info("deleteCascade START sessionId={} setId={} thread={}",
                sessionId, setId, Thread.currentThread().getName());
        try {
            cascade.processSetDelete(userId, sessionId, setId, oldValue);
        } catch (Exception e) {
            log.error("deleteCascade FAILED sessionId={} setId={}", sessionId, setId, e);
        } finally {
            markCompleteSafely(sessionId);
            log.info("deleteCascade DONE sessionId={} setId={} totalMs={}",
                    sessionId, setId, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    /**
     * Run the exercise-delete cascade off the HTTP thread. Reachable only
     * via admin paths today (frontend enforces ≥1 set per exercise).
     */
    @Async("prDetectionExecutor")
    public void runExerciseDeleteCascadeAsync(UUID userId, UUID sessionId,
                                               String exerciseId,
                                               List<LoggedSet> oldValues) {
        long t0 = System.nanoTime();
        log.info("exerciseDeleteCascade START sessionId={} exerciseId={} thread={}",
                sessionId, exerciseId, Thread.currentThread().getName());
        try {
            cascade.processExerciseDelete(userId, sessionId, exerciseId, oldValues);
        } catch (Exception e) {
            log.error("exerciseDeleteCascade FAILED sessionId={} exerciseId={}",
                    sessionId, exerciseId, e);
        } finally {
            markCompleteSafely(sessionId);
            log.info("exerciseDeleteCascade DONE sessionId={} exerciseId={} totalMs={}",
                    sessionId, exerciseId, (System.nanoTime() - t0) / 1_000_000);
        }
    }

    private void markCompleteSafely(UUID sessionId) {
        try {
            sessionRepo.markPrDetectionComplete(sessionId, Instant.now());
        } catch (Exception e) {
            log.error("Failed to mark prDetectionCompletedAt for session={} — flag stays null",
                    sessionId, e);
        }
    }
}
