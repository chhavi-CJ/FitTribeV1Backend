package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping for {@code user_exercise_bests} table (Flyway V44).
 *
 * <p>Mutable cache of maxima per user per exercise. Rebuilt from session
 * history by the nightly integrity job or on-demand via admin tools.
 * Used by PRDetector during set logging to determine if a new set is a PR.
 *
 * <h3>Exercise type semantics</h3>
 * {@code exercise_type} determines which signal columns are populated:
 * <ul>
 *   <li>WEIGHTED, BODYWEIGHT_UNASSISTED, BODYWEIGHT_ASSISTED, BODYWEIGHT_WEIGHTED:
 *       weight/rep signals populated, best_hold_seconds NULL</li>
 *   <li>TIMED: best_hold_seconds populated, weight/rep signals NULL</li>
 * </ul>
 *
 * <h3>Assisted/weighted bodyweight semantics</h3>
 * For assisted bodyweight exercises, weight columns store effective weight
 * (bodyweight − assistance). For weighted bodyweight, effective weight is
 * (bodyweight + added weight).
 */
@Entity
@Table(name = "user_exercise_bests")
@IdClass(UserExerciseBestsId.class)
public class UserExerciseBests {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "exercise_id", nullable = false, length = 50)
    private String exerciseId;

    @Column(name = "exercise_type", nullable = false, length = 30)
    private String exerciseType;

    @Column(name = "best_wt_kg", precision = 6, scale = 2)
    private BigDecimal bestWtKg;

    @Column(name = "reps_at_best_wt")
    private Integer repsAtBestWt;

    @Column(name = "best_reps")
    private Integer bestReps;

    @Column(name = "wt_at_best_reps_kg", precision = 6, scale = 2)
    private BigDecimal wtAtBestRepsKg;

    @Column(name = "best_1rm_epley_kg", precision = 6, scale = 2)
    private BigDecimal best1rmEpleyKg;

    @Column(name = "best_set_volume_kg", precision = 8, scale = 2)
    private BigDecimal bestSetVolumeKg;

    @Column(name = "best_session_volume_kg", precision = 10, scale = 2)
    private BigDecimal bestSessionVolumeKg;

    @Column(name = "best_hold_seconds")
    private Integer bestHoldSeconds;

    @Column(name = "total_sessions_with_exercise")
    private Integer totalSessionsWithExercise = 0;

    @Column(name = "last_logged_at")
    private Instant lastLoggedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public UserExerciseBests() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }

    public String getExerciseType() { return exerciseType; }
    public void setExerciseType(String exerciseType) { this.exerciseType = exerciseType; }

    public BigDecimal getBestWtKg() { return bestWtKg; }
    public void setBestWtKg(BigDecimal bestWtKg) { this.bestWtKg = bestWtKg; }

    public Integer getRepsAtBestWt() { return repsAtBestWt; }
    public void setRepsAtBestWt(Integer repsAtBestWt) { this.repsAtBestWt = repsAtBestWt; }

    public Integer getBestReps() { return bestReps; }
    public void setBestReps(Integer bestReps) { this.bestReps = bestReps; }

    public BigDecimal getWtAtBestRepsKg() { return wtAtBestRepsKg; }
    public void setWtAtBestRepsKg(BigDecimal wtAtBestRepsKg) { this.wtAtBestRepsKg = wtAtBestRepsKg; }

    public BigDecimal getBest1rmEpleyKg() { return best1rmEpleyKg; }
    public void setBest1rmEpleyKg(BigDecimal best1rmEpleyKg) { this.best1rmEpleyKg = best1rmEpleyKg; }

    public BigDecimal getBestSetVolumeKg() { return bestSetVolumeKg; }
    public void setBestSetVolumeKg(BigDecimal bestSetVolumeKg) { this.bestSetVolumeKg = bestSetVolumeKg; }

    public BigDecimal getBestSessionVolumeKg() { return bestSessionVolumeKg; }
    public void setBestSessionVolumeKg(BigDecimal bestSessionVolumeKg) { this.bestSessionVolumeKg = bestSessionVolumeKg; }

    public Integer getBestHoldSeconds() { return bestHoldSeconds; }
    public void setBestHoldSeconds(Integer bestHoldSeconds) { this.bestHoldSeconds = bestHoldSeconds; }

    public Integer getTotalSessionsWithExercise() { return totalSessionsWithExercise; }
    public void setTotalSessionsWithExercise(Integer totalSessionsWithExercise) { this.totalSessionsWithExercise = totalSessionsWithExercise; }

    public Instant getLastLoggedAt() { return lastLoggedAt; }
    public void setLastLoggedAt(Instant lastLoggedAt) { this.lastLoggedAt = lastLoggedAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserExerciseBests that = (UserExerciseBests) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(exerciseId, that.exerciseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, exerciseId);
    }
}
