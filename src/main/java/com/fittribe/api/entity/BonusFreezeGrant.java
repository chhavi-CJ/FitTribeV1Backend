package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bonus_freeze_grants")
public class BonusFreezeGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumption_reason")
    private String consumptionReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ── Required by JPA ──────────────────────────────────────────────
    public BonusFreezeGrant() {}

    // ── Getters / Setters ────────────────────────────────────────────
    public Long getId()              { return id; }
    public void setId(Long id)       { this.id = id; }

    public UUID getUserId()                  { return userId; }
    public void setUserId(UUID userId)       { this.userId = userId; }

    public Instant getEarnedAt()                 { return earnedAt; }
    public void setEarnedAt(Instant earnedAt)    { this.earnedAt = earnedAt; }

    public Instant getValidFrom()                { return validFrom; }
    public void setValidFrom(Instant validFrom)  { this.validFrom = validFrom; }

    public Instant getExpiresAt()                { return expiresAt; }
    public void setExpiresAt(Instant expiresAt)  { this.expiresAt = expiresAt; }

    public Instant getConsumedAt()                   { return consumedAt; }
    public void setConsumedAt(Instant consumedAt)    { this.consumedAt = consumedAt; }

    public String getConsumptionReason()                         { return consumptionReason; }
    public void setConsumptionReason(String consumptionReason)   { this.consumptionReason = consumptionReason; }

    public Instant getCreatedAt()    { return createdAt; }
}
