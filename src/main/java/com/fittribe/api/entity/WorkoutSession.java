package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workout_sessions")
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name")
    private String name;

    @Column(name = "badge")
    private String badge;

    @Column(name = "status")
    private String status = "IN_PROGRESS";

    // JSONB column stored as raw String; populated by the client if needed
    @Column(name = "exercises", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String exercises;

    @Column(name = "total_sets")
    private Integer totalSets = 0;

    @Column(name = "total_volume_kg")
    private BigDecimal totalVolumeKg = BigDecimal.ZERO;

    @Column(name = "duration_mins")
    private Integer durationMins;

    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "ai_insight")
    private String aiInsight;

    @Column(name = "ai_insight_generated_at")
    private Instant aiInsightGeneratedAt;

    // ── AI Phase 2 fields (V8 migration) ──────────────────────────────
    @Column(name = "ai_planned_weights", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String aiPlannedWeights;

    @Column(name = "exercises_skipped", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] exercisesSkipped;

    @Column(name = "exercises_added", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String exercisesAdded;

    @Column(name = "weekly_goal_hit")
    private Boolean weeklyGoalHit = false;

    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(name = "swap_log", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String swapLog = "[]";

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
    }

    // ── Required by JPA ───────────────────────────────────────────────
    public WorkoutSession() {}

    // ── Getters / Setters ─────────────────────────────────────────────
    public UUID getId()               { return id; }

    public UUID getUserId()           { return userId; }
    public void setUserId(UUID v)     { this.userId = v; }

    public String getName()           { return name; }
    public void setName(String v)     { this.name = v; }

    public String getBadge()          { return badge; }
    public void setBadge(String v)    { this.badge = v; }

    public String getStatus()         { return status; }
    public void setStatus(String v)   { this.status = v; }

    public String getExercises()      { return exercises; }
    public void setExercises(String v){ this.exercises = v; }

    public Integer getTotalSets()             { return totalSets; }
    public void setTotalSets(Integer v)       { this.totalSets = v; }

    public BigDecimal getTotalVolumeKg()          { return totalVolumeKg; }
    public void setTotalVolumeKg(BigDecimal v)    { this.totalVolumeKg = v; }

    public Integer getDurationMins()              { return durationMins; }
    public void setDurationMins(Integer v)        { this.durationMins = v; }

    public Instant getStartedAt()     { return startedAt; }

    public Instant getFinishedAt()        { return finishedAt; }
    public void setFinishedAt(Instant v)  { this.finishedAt = v; }

    public String getAiInsight()          { return aiInsight; }
    public void setAiInsight(String v)    { this.aiInsight = v; }

    public Instant getAiInsightGeneratedAt()         { return aiInsightGeneratedAt; }
    public void setAiInsightGeneratedAt(Instant v)   { this.aiInsightGeneratedAt = v; }

    public String getAiPlannedWeights()              { return aiPlannedWeights; }
    public void setAiPlannedWeights(String v)        { this.aiPlannedWeights = v; }

    public String[] getExercisesSkipped()            { return exercisesSkipped; }
    public void setExercisesSkipped(String[] v)      { this.exercisesSkipped = v; }

    public String getExercisesAdded()                { return exercisesAdded; }
    public void setExercisesAdded(String v)          { this.exercisesAdded = v; }

    public Boolean getWeeklyGoalHit()                { return weeklyGoalHit; }
    public void setWeeklyGoalHit(Boolean v)          { this.weeklyGoalHit = v; }

    public Integer getWeekNumber()                   { return weekNumber; }
    public void setWeekNumber(Integer v)             { this.weekNumber = v; }

    public String getSwapLog()                       { return swapLog; }
    public void setSwapLog(String v)                 { this.swapLog = v; }
}
