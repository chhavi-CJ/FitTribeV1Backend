package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_plan_generated")
public class DailyPlanGenerated {

    @EmbeddedId
    private DailyPlanGeneratedId id;

    @Column(name = "day_type")
    private String dayType;

    @Column(name = "exercises", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String exercises;

    @Column(name = "session_note")
    private String sessionNote;

    @Column(name = "cardio_suggestion", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String cardioSuggestion;

    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;

    public DailyPlanGenerated() {}

    public DailyPlanGeneratedId getId()              { return id; }
    public void setId(DailyPlanGeneratedId id)       { this.id = id; }

    public String getDayType()                       { return dayType; }
    public void setDayType(String dayType)           { this.dayType = dayType; }

    public String getExercises()                     { return exercises; }
    public void setExercises(String exercises)       { this.exercises = exercises; }

    public String getSessionNote()                   { return sessionNote; }
    public void setSessionNote(String sessionNote)   { this.sessionNote = sessionNote; }

    public String getCardioSuggestion()              { return cardioSuggestion; }
    public void setCardioSuggestion(String v)        { this.cardioSuggestion = v; }

    public Instant getGeneratedAt()                  { return generatedAt; }
}
