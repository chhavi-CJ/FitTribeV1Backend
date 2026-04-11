package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping for the {@code strength_score_history} table (Flyway V36).
 *
 * <p>Append-only weekly strength scores — one row per
 * {@code (user_id, muscle, week_start)}. The unique constraint
 * {@code uq_strength_score_user_muscle_week} on V36 makes every recompute
 * idempotent via the repository's {@code ON CONFLICT DO UPDATE} upsert.
 *
 * <h3>Muscle values</h3>
 * One of: {@code CHEST}, {@code BACK}, {@code LEGS}, {@code SHOULDERS},
 * {@code BICEPS}, {@code TRICEPS} — matching the
 * {@code TrendsMuscle} enum in {@code ProgressSnapshotService}.
 * Stored as a plain {@code String} to tolerate future enum additions
 * without a JPA {@code EnumType} migration.
 *
 * <h3>formula_version invariant</h3>
 * {@code formula_version} must stay in lockstep with
 * {@code exercise_strength_targets.formula_version}. Bump both
 * simultaneously when the scoring formula changes.
 *
 * <h3>Write path</h3>
 * Always use
 * {@link com.fittribe.api.repository.StrengthScoreHistoryRepository#upsert}
 * — not JPA {@code save()}, for the same reason as
 * {@link UserProgressSnapshot}.
 */
@Entity
@Table(name = "strength_score_history")
public class StrengthScoreHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** One of: CHEST | BACK | LEGS | SHOULDERS | BICEPS | TRICEPS. */
    @Column(name = "muscle", nullable = false, length = 20)
    private String muscle;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    /** Normalised 0–100 score computed from Epley 1RM vs target 1RM, capped at 100. */
    @Column(name = "strength_score", nullable = false)
    private Integer strengthScore;

    /**
     * Must stay in lockstep with {@code exercise_strength_targets.formula_version}.
     * Bump both columns simultaneously when the scoring formula changes.
     */
    @Column(name = "formula_version", nullable = false)
    private Integer formulaVersion = 1;

    /** Set by the database on insert and updated by the upsert query. Not writable from JPA. */
    @Column(name = "computed_at", insertable = false, updatable = false)
    private Instant computedAt;

    public StrengthScoreHistory() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getMuscle() { return muscle; }
    public void setMuscle(String muscle) { this.muscle = muscle; }

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public Integer getStrengthScore() { return strengthScore; }
    public void setStrengthScore(Integer strengthScore) { this.strengthScore = strengthScore; }

    public Integer getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(Integer formulaVersion) { this.formulaVersion = formulaVersion; }

    public Instant getComputedAt() { return computedAt; }
}
