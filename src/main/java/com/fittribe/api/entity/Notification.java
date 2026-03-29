package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type")
    private String type;

    @Column(name = "title")
    private String title;

    @Column(name = "body")
    private String body;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Notification() {}

    public UUID getId()              { return id; }

    public UUID getUserId()          { return userId; }
    public void setUserId(UUID v)    { this.userId = v; }

    public String getType()          { return type; }
    public void setType(String v)    { this.type = v; }

    public String getTitle()         { return title; }
    public void setTitle(String v)   { this.title = v; }

    public String getBody()          { return body; }
    public void setBody(String v)    { this.body = v; }

    public Boolean getIsRead()       { return isRead; }
    public void setIsRead(Boolean v) { this.isRead = v; }

    public Instant getCreatedAt()    { return createdAt; }
}
