package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "event_data", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String eventData = "{}";

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

    public String getEventData()         { return eventData; }
    public void   setEventData(String v) { this.eventData = v != null ? v : "{}"; }
}
