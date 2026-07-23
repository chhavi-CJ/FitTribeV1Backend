package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_weekly_progress")
public class GroupWeeklyProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "iso_year", nullable = false)
    private int isoYear;

    @Column(name = "iso_week", nullable = false)
    private int isoWeek;

    @Column(name = "target_sessions", nullable = false)
    private int targetSessions;

    @Column(name = "sessions_logged", nullable = false)
    private int sessionsLogged = 0;

    @Column(name = "current_tier", nullable = false, length = 10)
    private String currentTier = "NONE";

    @Column(name = "overachiever", nullable = false)
    private boolean overachiever = false;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "goal_metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String goalMetadata;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public GroupWeeklyProgress() {
        this.updatedAt = Instant.now();
    }

    public UUID getId()                 { return id; }

    public UUID getGroupId()            { return groupId; }
    public void setGroupId(UUID v)      { this.groupId = v; }

    public int getIsoYear()             { return isoYear; }
    public void setIsoYear(int v)       { this.isoYear = v; }

    public int getIsoWeek()             { return isoWeek; }
    public void setIsoWeek(int v)       { this.isoWeek = v; }

    public int getTargetSessions()      { return targetSessions; }
    public void setTargetSessions(int v){ this.targetSessions = v; }

    public int getSessionsLogged()      { return sessionsLogged; }
    public void setSessionsLogged(int v){ this.sessionsLogged = v; }

    public String getCurrentTier()         { return currentTier; }
    public void   setCurrentTier(String v) { this.currentTier = v; }

    public boolean isOverachiever()         { return overachiever; }
    public void    setOverachiever(boolean v){ this.overachiever = v; }

    public Instant getLockedAt()           { return lockedAt; }
    public void    setLockedAt(Instant v)  { this.lockedAt = v; }

    public String getGoalMetadata()           { return goalMetadata; }
    public void   setGoalMetadata(String v)   { this.goalMetadata = v; }

    public Instant getCreatedAt()          { return createdAt; }

    public Instant getUpdatedAt()          { return updatedAt; }
    public void    setUpdatedAt(Instant v) { this.updatedAt = v; }
}
