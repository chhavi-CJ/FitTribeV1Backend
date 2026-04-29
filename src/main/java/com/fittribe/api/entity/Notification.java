package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "type", length = 40, nullable = false)
    private String type;

    @Column(name = "feed_item_id")
    private UUID feedItemId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Notification() {}

    public UUID getId()                            { return id; }

    public UUID getRecipientId()                   { return recipientId; }
    public void setRecipientId(UUID v)             { this.recipientId = v; }

    public UUID getActorId()                       { return actorId; }
    public void setActorId(UUID v)                 { this.actorId = v; }

    public String getType()                        { return type; }
    public void setType(String v)                  { this.type = v; }

    public UUID getFeedItemId()                    { return feedItemId; }
    public void setFeedItemId(UUID v)              { this.feedItemId = v; }

    public UUID getGroupId()                       { return groupId; }
    public void setGroupId(UUID v)                 { this.groupId = v; }

    public Map<String, Object> getMetadata()       { return metadata; }
    public void setMetadata(Map<String, Object> v) { this.metadata = v; }

    public OffsetDateTime getReadAt()              { return readAt; }
    public void setReadAt(OffsetDateTime v)        { this.readAt = v; }

    public OffsetDateTime getCreatedAt()           { return createdAt; }
}
