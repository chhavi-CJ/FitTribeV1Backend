package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_weekly_stats")
public class UserWeeklyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "sessions_count", nullable = false)
    private int sessionsCount = 0;

    @Column(name = "total_volume_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVolumeKg = BigDecimal.ZERO;

    @Column(name = "prs_hit", nullable = false)
    private int prsHit = 0;

    @Column(name = "weekly_goal_target", nullable = false)
    private int weeklyGoalTarget;

    @Column(name = "weekly_goal_hit", nullable = false)
    private boolean weeklyGoalHit = false;

    @Column(name = "sessions_with_3plus_sets", nullable = false)
    private int sessionsWith3PlusSets = 0;

    @Column(name = "sessions_45min_plus", nullable = false)
    private int sessions45MinPlus = 0;

    @Column(name = "baseline_volume_kg", precision = 10, scale = 2)
    private BigDecimal baselineVolumeKg;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt = Instant.now();

    public UserWeeklyStats() {}

    public UUID getId()                         { return id; }

    public UUID getUserId()                     { return userId; }
    public void setUserId(UUID v)               { this.userId = v; }

    public LocalDate getWeekStartDate()         { return weekStartDate; }
    public void setWeekStartDate(LocalDate v)   { this.weekStartDate = v; }

    public int getSessionsCount()               { return sessionsCount; }
    public void setSessionsCount(int v)         { this.sessionsCount = v; }

    public BigDecimal getTotalVolumeKg()             { return totalVolumeKg; }
    public void setTotalVolumeKg(BigDecimal v)       { this.totalVolumeKg = v; }

    public int getPrsHit()                      { return prsHit; }
    public void setPrsHit(int v)                { this.prsHit = v; }

    public int getWeeklyGoalTarget()            { return weeklyGoalTarget; }
    public void setWeeklyGoalTarget(int v)      { this.weeklyGoalTarget = v; }

    public boolean isWeeklyGoalHit()            { return weeklyGoalHit; }
    public void setWeeklyGoalHit(boolean v)     { this.weeklyGoalHit = v; }

    public int getSessionsWith3PlusSets()       { return sessionsWith3PlusSets; }
    public void setSessionsWith3PlusSets(int v) { this.sessionsWith3PlusSets = v; }

    public int getSessions45MinPlus()           { return sessions45MinPlus; }
    public void setSessions45MinPlus(int v)     { this.sessions45MinPlus = v; }

    public BigDecimal getBaselineVolumeKg()          { return baselineVolumeKg; }
    public void setBaselineVolumeKg(BigDecimal v)    { this.baselineVolumeKg = v; }

    public Instant getComputedAt()              { return computedAt; }
    public void setComputedAt(Instant v)        { this.computedAt = v; }
}
