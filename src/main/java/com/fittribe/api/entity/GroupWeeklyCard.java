package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_weekly_cards")
public class GroupWeeklyCard {

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

    @Column(name = "final_tier", nullable = false, length = 10)
    private String finalTier;

    @Column(name = "sessions_logged", nullable = false)
    private int sessionsLogged;

    @Column(name = "target_sessions", nullable = false)
    private int targetSessions;

    @Column(name = "final_percentage", nullable = false)
    private int finalPercentage;

    @Column(name = "overachiever", nullable = false)
    private boolean overachiever = false;

    @Column(name = "streak_at_lock", nullable = false)
    private int streakAtLock = 1;

    @Column(name = "contributor_user_ids", columnDefinition = "uuid[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] contributorUserIds = new UUID[0];

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "locked_at", insertable = false, updatable = false)
    private Instant lockedAt;

    public GroupWeeklyCard() {}

    public UUID getId()                 { return id; }

    public UUID getGroupId()            { return groupId; }
    public void setGroupId(UUID v)      { this.groupId = v; }

    public int getIsoYear()             { return isoYear; }
    public void setIsoYear(int v)       { this.isoYear = v; }

    public int getIsoWeek()             { return isoWeek; }
    public void setIsoWeek(int v)       { this.isoWeek = v; }

    public String getFinalTier()           { return finalTier; }
    public void   setFinalTier(String v)   { this.finalTier = v; }

    public int getSessionsLogged()      { return sessionsLogged; }
    public void setSessionsLogged(int v){ this.sessionsLogged = v; }

    public int getTargetSessions()      { return targetSessions; }
    public void setTargetSessions(int v){ this.targetSessions = v; }

    public int getFinalPercentage()      { return finalPercentage; }
    public void setFinalPercentage(int v){ this.finalPercentage = v; }

    public boolean isOverachiever()          { return overachiever; }
    public void    setOverachiever(boolean v){ this.overachiever = v; }

    public int getStreakAtLock()         { return streakAtLock; }
    public void setStreakAtLock(int v)   { this.streakAtLock = v; }

    public UUID[] getContributorUserIds()           { return contributorUserIds; }
    public void   setContributorUserIds(UUID[] v)   { this.contributorUserIds = v; }

    public String getMetadata()           { return metadata; }
    public void   setMetadata(String v)   { this.metadata = v; }

    public Instant getLockedAt()          { return lockedAt; }
}
