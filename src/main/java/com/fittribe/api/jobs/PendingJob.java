package com.fittribe.api.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA mapping for the {@code pending_jobs} table (Flyway V33).
 *
 * <p>This is a generic async job row — the worker claims the next
 * pending row, routes on {@link #jobType}, and persists the outcome.
 * Payload is stored as raw JSONB (mapped to a {@code String} here, the
 * same pattern as {@code workout_sessions.exercises}) so producers can
 * evolve their shape without a migration per job type.
 *
 * <p>Field status values (strings, not enums, to tolerate rolling
 * deploys and old rows):
 * <ul>
 *   <li>{@code pending}  — waiting to be picked up</li>
 *   <li>{@code running}  — a worker has claimed it</li>
 *   <li>{@code completed} — dispatch succeeded</li>
 *   <li>{@code failed}   — exhausted all retries</li>
 * </ul>
 */
@Entity
@Table(name = "pending_jobs")
public class PendingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "job_type", nullable = false, length = 50)
    private String jobType;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload = "{}";

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error")
    private String error;

    public PendingJob() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
