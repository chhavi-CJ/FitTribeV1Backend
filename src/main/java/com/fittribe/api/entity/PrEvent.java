package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA mapping for {@code pr_events} table (Flyway V44).
 *
 * <p>Append-only log of every PR event, partitioned by {@code week_start}
 * using PostgreSQL 11+ declarative RANGE partitioning. Source of truth for
 * history, weekly reports, and audit trails.
 *
 * <h3>Supersession semantics</h3>
 * When a user edits a set within the 5am window, the original PR event is
 * marked with {@code superseded_at = now()} and optionally linked to the
 * replacement event via {@code superseded_by}. Active-events-only queries
 * filter on {@code WHERE superseded_at IS NULL} (partial index optimized).
 *
 * <h3>JSONB columns</h3>
 * {@code signals_met} and {@code value_payload} are typed as {@link Map} with
 * {@code @JdbcTypeCode(SqlTypes.JSON)}. Hibernate 6 handles Jackson
 * serialization/deserialization natively on Map fields. Using String fields
 * with this annotation causes Hibernate to corrupt JSON on entity load
 * (deserializes to Map internally, then calls Map.toString() to coerce back
 * to String, producing {@code {key=value}} which is not valid JSON).
 *
 * <h3>Partitioning</h3>
 * Partitioned by {@code week_start} (not created_at) so a weekly report query
 * to a specific week stays within a single partition. This matters because an
 * edit today can supersede an event from last week.
 */
@Entity
@Table(name = "pr_events")
public class PrEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false, length = 50)
    private String exerciseId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "set_id", nullable = false)
    private UUID setId;

    @Column(name = "pr_category", nullable = false, length = 30)
    private String prCategory;
    // FIRST_EVER, WEIGHT_PR, REP_PR, VOLUME_PR, MAX_ATTEMPT

    @Column(name = "signals_met", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> signalsMet = new HashMap<>();
    // { "weight": true, "rep": false, "volume": true, "one_rm": true }

    @Column(name = "value_payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> valuePayload = new HashMap<>();
    // Structured: { "delta_kg": 5, "previous_best": {...}, "new_best": {...} }

    @Column(name = "coins_awarded", nullable = false)
    private Integer coinsAwarded = 0;

    @Column(name = "detector_version", nullable = false, length = 20)
    private String detectorVersion = "v1.0";

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    public PrEvent() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getSetId() { return setId; }
    public void setSetId(UUID setId) { this.setId = setId; }

    public String getPrCategory() { return prCategory; }
    public void setPrCategory(String prCategory) { this.prCategory = prCategory; }

    public Map<String, Object> getSignalsMet() { return signalsMet; }
    public void setSignalsMet(Map<String, Object> signalsMet) { this.signalsMet = signalsMet; }

    public Map<String, Object> getValuePayload() { return valuePayload; }
    public void setValuePayload(Map<String, Object> valuePayload) { this.valuePayload = valuePayload; }

    public Integer getCoinsAwarded() { return coinsAwarded; }
    public void setCoinsAwarded(Integer coinsAwarded) { this.coinsAwarded = coinsAwarded; }

    public String getDetectorVersion() { return detectorVersion; }
    public void setDetectorVersion(String detectorVersion) { this.detectorVersion = detectorVersion; }

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getSupersededAt() { return supersededAt; }
    public void setSupersededAt(Instant supersededAt) { this.supersededAt = supersededAt; }

    public UUID getSupersededBy() { return supersededBy; }
    public void setSupersededBy(UUID supersededBy) { this.supersededBy = supersededBy; }
}
