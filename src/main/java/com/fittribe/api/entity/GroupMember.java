package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members")
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role")
    private String role = "MEMBER";

    @Column(name = "joined_at", insertable = false, updatable = false)
    private Instant joinedAt;

    public GroupMember() {}

    public UUID getId()              { return id; }

    public UUID getGroupId()         { return groupId; }
    public void setGroupId(UUID v)   { this.groupId = v; }

    public UUID getUserId()          { return userId; }
    public void setUserId(UUID v)    { this.userId = v; }

    public String getRole()          { return role; }
    public void setRole(String v)    { this.role = v; }

    public Instant getJoinedAt()     { return joinedAt; }
}
