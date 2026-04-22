package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for {@code user_fitness_summary} table (Flyway V49).
 *
 * <p>One row per user (1:1). Upserted nightly by {@code FitnessSummaryScheduler}
 * at 3am IST. The {@code summary} column stores the serialised
 * {@link com.fittribe.api.fitnesssummary.FitnessSummary} DTO as JSONB.
 */
@Entity
@Table(name = "user_fitness_summary")
public class UserFitnessSummary {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Serialised FitnessSummary JSON. */
    @Column(name = "summary", nullable = false, columnDefinition = "jsonb")
    private String summary;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "sample_window", nullable = false)
    private String sampleWindow;

    public UserFitnessSummary() {}

    // ── Getters / Setters ─────────────────────────────────────────────

    public UUID getUserId()                      { return userId; }
    public void setUserId(UUID userId)           { this.userId = userId; }

    public String getSummary()                   { return summary; }
    public void setSummary(String summary)       { this.summary = summary; }

    public Instant getComputedAt()               { return computedAt; }
    public void setComputedAt(Instant computedAt){ this.computedAt = computedAt; }

    public String getSampleWindow()              { return sampleWindow; }
    public void setSampleWindow(String w)        { this.sampleWindow = w; }
}
