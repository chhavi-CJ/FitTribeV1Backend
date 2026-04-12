package com.fittribe.api.bonus;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Entity for a generated bonus session row in bonus_session_generated.
 * Mirrors daily_plan_generated structure, minus cardio_suggestion
 * (bonus sessions ARE the cardio alternative, so no nested suggestion).
 */
@Entity
@Table(name = "bonus_session_generated")
public class BonusSessionGenerated {

    @EmbeddedId
    private BonusSessionGeneratedId id;

    @Column(name = "archetype", nullable = false)
    private String archetype;

    @Column(name = "archetype_rationale", columnDefinition = "text")
    private String archetypeRationale;

    @Column(name = "exercises", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String exercises;

    @Column(name = "session_note", columnDefinition = "text")
    private String sessionNote;

    @Column(name = "day_coach_tip", columnDefinition = "text")
    private String dayCoachTip;

    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;

    public BonusSessionGenerated() {}

    public BonusSessionGeneratedId getId() { return id; }
    public void setId(BonusSessionGeneratedId v) { this.id = v; }

    public String getArchetype() { return archetype; }
    public void   setArchetype(String v) { this.archetype = v; }

    public String getArchetypeRationale() { return archetypeRationale; }
    public void   setArchetypeRationale(String v) { this.archetypeRationale = v; }

    public String getExercises() { return exercises; }
    public void   setExercises(String v) { this.exercises = v; }

    public String getSessionNote() { return sessionNote; }
    public void   setSessionNote(String v) { this.sessionNote = v; }

    public String getDayCoachTip() { return dayCoachTip; }
    public void   setDayCoachTip(String v) { this.dayCoachTip = v; }

    public Instant getGeneratedAt() { return generatedAt; }
}
