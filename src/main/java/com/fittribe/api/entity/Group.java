package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"groups\"")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "icon")
    private String icon;

    @Column(name = "color")
    private String color;

    @Column(name = "invite_code", unique = true)
    private String inviteCode;

    @Column(name = "streak")
    private Integer streak = 0;

    @Column(name = "weekly_goal")
    private Integer weeklyGoal = 4;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Group() {}

    public UUID getId()                  { return id; }

    public String getName()              { return name; }
    public void setName(String v)        { this.name = v; }

    public String getIcon()              { return icon; }
    public void setIcon(String v)        { this.icon = v; }

    public String getColor()             { return color; }
    public void setColor(String v)       { this.color = v; }

    public String getInviteCode()        { return inviteCode; }
    public void setInviteCode(String v)  { this.inviteCode = v; }

    public Integer getStreak()           { return streak; }
    public void setStreak(Integer v)     { this.streak = v; }

    public Integer getWeeklyGoal()       { return weeklyGoal; }
    public void setWeeklyGoal(Integer v) { this.weeklyGoal = v; }

    public UUID getCreatedBy()           { return createdBy; }
    public void setCreatedBy(UUID v)     { this.createdBy = v; }

    public Instant getCreatedAt()        { return createdAt; }
}
