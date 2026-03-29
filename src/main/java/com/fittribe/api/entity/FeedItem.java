package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feed_items")
public class FeedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "type")
    private String type;

    @Column(name = "body")
    private String body;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public FeedItem() {}

    public UUID getId()              { return id; }

    public UUID getGroupId()         { return groupId; }
    public void setGroupId(UUID v)   { this.groupId = v; }

    public UUID getUserId()          { return userId; }
    public void setUserId(UUID v)    { this.userId = v; }

    public String getType()          { return type; }
    public void setType(String v)    { this.type = v; }

    public String getBody()          { return body; }
    public void setBody(String v)    { this.body = v; }

    public Instant getCreatedAt()    { return createdAt; }
}
