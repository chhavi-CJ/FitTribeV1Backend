package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_member_goal_snapshot")
public class GroupMemberGoalSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "iso_year", nullable = false)
    private int isoYear;

    @Column(name = "iso_week", nullable = false)
    private int isoWeek;

    @Column(name = "weekly_goal", nullable = false)
    private int weeklyGoal;

    @Column(name = "sessions_contributed", nullable = false)
    private int sessionsContributed = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "joined_this_week", nullable = false)
    private Boolean joinedThisWeek = false;

    @Column(name = "left_this_week", nullable = false)
    private Boolean leftThisWeek = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public GroupMemberGoalSnapshot() {
        this.updatedAt = Instant.now();
    }

    public UUID getId()                 { return id; }

    public UUID getGroupId()            { return groupId; }
    public void setGroupId(UUID v)      { this.groupId = v; }

    public UUID getUserId()             { return userId; }
    public void setUserId(UUID v)       { this.userId = v; }

    public int getIsoYear()             { return isoYear; }
    public void setIsoYear(int v)       { this.isoYear = v; }

    public int getIsoWeek()             { return isoWeek; }
    public void setIsoWeek(int v)       { this.isoWeek = v; }

    public int getWeeklyGoal()          { return weeklyGoal; }
    public void setWeeklyGoal(int v)    { this.weeklyGoal = v; }

    public int getSessionsContributed()      { return sessionsContributed; }
    public void setSessionsContributed(int v){ this.sessionsContributed = v; }

    public Boolean getIsActive()            { return isActive; }
    public void    setIsActive(Boolean v)   { this.isActive = v; }

    public Boolean getJoinedThisWeek()          { return joinedThisWeek; }
    public void    setJoinedThisWeek(Boolean v) { this.joinedThisWeek = v; }

    public Boolean getLeftThisWeek()            { return leftThisWeek; }
    public void    setLeftThisWeek(Boolean v)   { this.leftThisWeek = v; }

    public Instant getCreatedAt()          { return createdAt; }

    public Instant getUpdatedAt()          { return updatedAt; }
    public void    setUpdatedAt(Instant v) { this.updatedAt = v; }
}
