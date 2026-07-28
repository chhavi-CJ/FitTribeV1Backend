package com.fittribe.api.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "event_properties", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode eventProperties;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public AnalyticsEvent() {}

    public AnalyticsEvent(UUID userId, String eventName) {
        this.userId = userId;
        this.eventName = eventName;
    }

    public Long getId()                     { return id; }

    public UUID getUserId()                 { return userId; }
    public void setUserId(UUID v)           { this.userId = v; }

    public String getEventName()            { return eventName; }
    public void setEventName(String v)      { this.eventName = v; }

    public JsonNode getEventProperties()    { return eventProperties; }
    public void setEventProperties(JsonNode v) { this.eventProperties = v; }

    public String getSessionId()            { return sessionId; }
    public void setSessionId(String v)      { this.sessionId = v; }

    public String getPlatform()             { return platform; }
    public void setPlatform(String v)       { this.platform = v; }

    public String getAppVersion()           { return appVersion; }
    public void setAppVersion(String v)     { this.appVersion = v; }

    public Instant getCreatedAt()           { return createdAt; }
}
