package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "freeze_transactions")
public class FreezeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private int amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ── Required by JPA ──────────────────────────────────────────────
    public FreezeTransaction() {}

    // ── Getters / Setters ────────────────────────────────────────────
    public Long getId()              { return id; }
    public void setId(Long id)       { this.id = id; }

    public UUID getUserId()                  { return userId; }
    public void setUserId(UUID userId)       { this.userId = userId; }

    public String getEventType()                     { return eventType; }
    public void setEventType(String eventType)       { this.eventType = eventType; }

    public int getAmount()               { return amount; }
    public void setAmount(int amount)    { this.amount = amount; }

    public Instant getOccurredAt()                     { return occurredAt; }
    public void setOccurredAt(Instant occurredAt)      { this.occurredAt = occurredAt; }

    public Map<String, Object> getMetadata()                  { return metadata; }
    public void setMetadata(Map<String, Object> metadata)     { this.metadata = metadata; }

    public Instant getCreatedAt()    { return createdAt; }
}
