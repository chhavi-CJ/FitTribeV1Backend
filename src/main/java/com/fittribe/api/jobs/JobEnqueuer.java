package com.fittribe.api.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Thin producer API for the {@code pending_jobs} queue. Callers hand
 * over a {@link JobType} plus a payload map; this service serializes
 * the payload to JSONB and inserts a new {@code pending} row.
 *
 * <p>Kept as its own class (rather than a static helper on
 * {@link PendingJobRepository}) so producers depend on a small,
 * well-named seam that is easy to mock in unit tests.
 */
@Service
public class JobEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(JobEnqueuer.class);

    private final PendingJobRepository repo;
    private final ObjectMapper mapper;

    public JobEnqueuer(PendingJobRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    /**
     * Insert a new job row. The caller's payload is serialized to a
     * JSON string; the row is created with {@code status='pending'},
     * {@code attempts=0}, and {@code scheduled_for=NOW()} so a worker
     * can pick it up on its next tick.
     *
     * @return the generated job ID
     * @throws IllegalArgumentException if the payload can't be serialized
     */
    public Long enqueue(JobType type, Map<String, Object> payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "JobEnqueuer: payload for " + type + " is not JSON-serializable", e);
        }

        PendingJob job = new PendingJob();
        job.setJobType(type.name());
        job.setPayload(json);
        job.setStatus("pending");
        job.setAttempts(0);
        Instant now = Instant.now();
        job.setCreatedAt(now);
        job.setScheduledFor(now);

        PendingJob saved = repo.save(job);
        log.info("JobEnqueuer: enqueued {} job id={}", type, saved.getId());
        return saved.getId();
    }
}
