package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_insights")
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "insight_id", updatable = false, nullable = false)
    private UUID insightId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "insight_type")
    private String insightType;

    // Repurposed for weekly reports: stores the weekNumber as a string
    @Column(name = "muscle_group")
    private String muscleGroup;

    // Repurposed for weekly reports: stores weekNumber.toString()
    @Column(name = "exercise_name")
    private String exerciseName;

    @Column(name = "finding", columnDefinition = "TEXT")
    private String finding;

    @Column(name = "user_action_taken")
    private String userActionTaken;

    @Column(name = "applied_to_plan_id")
    private UUID appliedToPlanId;

    public AiInsight() {}

    public UUID getInsightId()                   { return insightId; }

    public UUID getUserId()                      { return userId; }
    public void setUserId(UUID v)                { this.userId = v; }

    public Instant getGeneratedAt()              { return generatedAt; }

    public String getInsightType()               { return insightType; }
    public void setInsightType(String v)         { this.insightType = v; }

    public String getMuscleGroup()               { return muscleGroup; }
    public void setMuscleGroup(String v)         { this.muscleGroup = v; }

    public String getExerciseName()              { return exerciseName; }
    public void setExerciseName(String v)        { this.exerciseName = v; }

    public String getFinding()                   { return finding; }
    public void setFinding(String v)             { this.finding = v; }

    public String getUserActionTaken()           { return userActionTaken; }
    public void setUserActionTaken(String v)     { this.userActionTaken = v; }

    public UUID getAppliedToPlanId()             { return appliedToPlanId; }
    public void setAppliedToPlanId(UUID v)       { this.appliedToPlanId = v; }
}
