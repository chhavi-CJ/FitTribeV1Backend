package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * JPA mapping for the {@code exercise_strength_targets} table (Flyway V37).
 *
 * <p>Reference data — one row per {@code (exercise_id, gender, fitness_level)}.
 * Only {@code BEGINNER} rows are seeded in v1; {@code INTERMEDIATE} and
 * {@code ADVANCED} rows are added in a future migration.
 *
 * <h3>is_bodyweight</h3>
 * Not stored here to avoid sync risk with {@code exercises.is_bodyweight}
 * (Flyway V11). {@code StrengthScoreService} derives it at lookup time via a
 * JOIN to the {@code exercises} table through
 * {@link com.fittribe.api.repository.ExerciseRepository}.
 *
 * <h3>multiplier vs repTarget</h3>
 * Exactly one of these is non-null per row:
 * <ul>
 *   <li>{@code multiplier} — weighted exercises: {@code targetRM = multiplier × bodyweightKg}</li>
 *   <li>{@code repTarget}  — bodyweight exercises: {@code targetRM = bodyweightKg × (1 + repTarget / 30.0)}</li>
 * </ul>
 *
 * <h3>formula_version invariant</h3>
 * {@code formula_version} must stay in lockstep with
 * {@code strength_score_history.formula_version}. Bump both
 * simultaneously when the scoring formula changes.
 */
@Entity
@Table(name = "exercise_strength_targets")
@IdClass(ExerciseStrengthTargetId.class)
public class ExerciseStrengthTarget {

    @Id
    @Column(name = "exercise_id", nullable = false, length = 50)
    private String exerciseId;

    /** One of: {@code MALE}, {@code FEMALE}. */
    @Id
    @Column(name = "gender", nullable = false, length = 10)
    private String gender;

    /** One of: {@code BEGINNER}, {@code INTERMEDIATE}, {@code ADVANCED}. */
    @Id
    @Column(name = "fitness_level", nullable = false, length = 20)
    private String fitnessLevel;

    /**
     * Target 1RM as a multiple of the user's bodyweight.
     * Null for bodyweight exercises — use {@link #repTarget} instead.
     */
    @Column(name = "multiplier", precision = 4, scale = 2)
    private BigDecimal multiplier;

    /**
     * Target rep count for bodyweight exercises.
     * {@code targetRM = bodyweightKg × (1 + repTarget / 30.0)}.
     * Null for weighted exercises — use {@link #multiplier} instead.
     */
    @Column(name = "rep_target")
    private Integer repTarget;

    /**
     * Must stay in lockstep with {@code strength_score_history.formula_version}.
     * Bump both columns simultaneously when the scoring formula changes.
     */
    @Column(name = "formula_version", nullable = false)
    private Integer formulaVersion = 1;

    public ExerciseStrengthTarget() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public String getExerciseId()   { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }

    public String getGender()       { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getFitnessLevel() { return fitnessLevel; }
    public void setFitnessLevel(String fitnessLevel) { this.fitnessLevel = fitnessLevel; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public Integer getRepTarget() { return repTarget; }
    public void setRepTarget(Integer repTarget) { this.repTarget = repTarget; }

    public Integer getFormulaVersion() { return formulaVersion; }
    public void setFormulaVersion(Integer formulaVersion) { this.formulaVersion = formulaVersion; }
}
