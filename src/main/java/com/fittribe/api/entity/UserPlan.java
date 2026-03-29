package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_plans")
public class UserPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id", updatable = false, nullable = false)
    private UUID planId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "days", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String days;

    @Column(name = "ai_rationale", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String aiRationale;

    @Column(name = "generated_at", insertable = false, updatable = false)
    private Instant generatedAt;

    public UserPlan() {}

    public UUID getPlanId()                  { return planId; }

    public UUID getUserId()                  { return userId; }
    public void setUserId(UUID v)            { this.userId = v; }

    public LocalDate getWeekStartDate()      { return weekStartDate; }
    public void setWeekStartDate(LocalDate v){ this.weekStartDate = v; }

    public Integer getWeekNumber()           { return weekNumber; }
    public void setWeekNumber(Integer v)     { this.weekNumber = v; }

    public String getDays()                  { return days; }
    public void setDays(String v)            { this.days = v; }

    public String getAiRationale()           { return aiRationale; }
    public void setAiRationale(String v)     { this.aiRationale = v; }

    public Instant getGeneratedAt()          { return generatedAt; }
}
