package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code pending_recalibrations} table (Flyway V34).
 *
 * <p>One row per weight-target adjustment produced by a weekly report
 * run. Rows are inserted by {@code WeeklyReportComputer} when a
 * too-light / pr_regression finding fires, then consumed by the next AI
 * plan generator run for the user's upcoming week. When the generator
 * applies a recalibration it sets {@code applied_at=NOW()} and stamps
 * {@code applied_to_plan_id} with the new plan's UUID — the row stays
 * in the table for audit but drops out of the partial
 * {@code idx_recalibrations_user_unapplied} index.
 *
 * <p>Side-table design (not mutating {@code user_plans.days} JSONB in
 * place) is explained in the V34 migration header: it keeps plan
 * documents immutable after generation and makes the "what we're fixing"
 * section on the Weekly Summary derivable from
 * {@code weekly_reports.recalibrations} JSONB + this table without
 * touching historical plans.
 */
@Entity
@Table(name = "pending_recalibrations")
public class PendingRecalibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false, length = 50)
    private String exerciseId;

    /** Nullable in the schema — custom workouts may not have a plan target. */
    @Column(name = "old_target_kg", precision = 6, scale = 2)
    private BigDecimal oldTargetKg;

    @Column(name = "new_target_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal newTargetKg;

    @Column(name = "reason", nullable = false)
    private String reason;

    /** Set by the plan generator once this recalibration has been applied. */
    @Column(name = "applied_to_plan_id")
    private UUID appliedToPlanId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    public PendingRecalibration() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }

    public BigDecimal getOldTargetKg() { return oldTargetKg; }
    public void setOldTargetKg(BigDecimal oldTargetKg) { this.oldTargetKg = oldTargetKg; }

    public BigDecimal getNewTargetKg() { return newTargetKg; }
    public void setNewTargetKg(BigDecimal newTargetKg) { this.newTargetKg = newTargetKg; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public UUID getAppliedToPlanId() { return appliedToPlanId; }
    public void setAppliedToPlanId(UUID appliedToPlanId) { this.appliedToPlanId = appliedToPlanId; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
}
