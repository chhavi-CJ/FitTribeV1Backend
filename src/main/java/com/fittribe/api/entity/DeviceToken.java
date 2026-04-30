package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_tokens")
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "platform", nullable = false, length = 10)
    private String platform;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public DeviceToken() {}

    public UUID getId()                  { return id; }

    public UUID getUserId()              { return userId; }
    public void setUserId(UUID v)        { this.userId = v; }

    public String getToken()             { return token; }
    public void setToken(String v)       { this.token = v; }

    public String getPlatform()          { return platform; }
    public void setPlatform(String v)    { this.platform = v; }

    public Instant getCreatedAt()        { return createdAt; }

    public Instant getLastSeenAt()       { return lastSeenAt; }
    public void setLastSeenAt(Instant v) { this.lastSeenAt = v; }
}
