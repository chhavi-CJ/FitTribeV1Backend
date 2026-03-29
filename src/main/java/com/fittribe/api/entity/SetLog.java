package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "set_logs")
public class SetLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "exercise_id", nullable = false)
    private String exerciseId;

    @Column(name = "exercise_name")
    private String exerciseName;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "reps")
    private Integer reps;

    @Column(name = "is_pr")
    private Boolean isPr = false;

    @Column(name = "logged_at", insertable = false, updatable = false)
    private Instant loggedAt;

    // ── Required by JPA ───────────────────────────────────────────────
    public SetLog() {}

    // ── Getters / Setters ─────────────────────────────────────────────
    public UUID getId()               { return id; }

    public UUID getSessionId()            { return sessionId; }
    public void setSessionId(UUID v)      { this.sessionId = v; }

    public String getExerciseId()             { return exerciseId; }
    public void setExerciseId(String v)       { this.exerciseId = v; }

    public String getExerciseName()               { return exerciseName; }
    public void setExerciseName(String v)         { this.exerciseName = v; }

    public Integer getSetNumber()             { return setNumber; }
    public void setSetNumber(Integer v)       { this.setNumber = v; }

    public BigDecimal getWeightKg()           { return weightKg; }
    public void setWeightKg(BigDecimal v)     { this.weightKg = v; }

    public Integer getReps()          { return reps; }
    public void setReps(Integer v)    { this.reps = v; }

    public Boolean getIsPr()          { return isPr; }
    public void setIsPr(Boolean v)    { this.isPr = v; }

    public Instant getLoggedAt()      { return loggedAt; }
}
