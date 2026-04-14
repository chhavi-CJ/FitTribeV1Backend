package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coin_transactions")
public class CoinTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "direction")
    private String direction;

    @Column(name = "label")
    private String label;

    @Column(name = "type")
    private String type;

    @Column(name = "reference_id")
    private String referenceId;

    // PR System V2 ledger columns (added V44)
    @Column(name = "delta")
    private Integer delta;

    @Column(name = "reason")
    private String reason;
    // WORKOUT_LOGGED, PR_AWARDED, FIRST_EVER, WEEKLY_GOAL, STREAK_MILESTONE,
    // IMPROVE_VS_LAST_WEEK, SHARE_WITH_STATS, STREAK_FREEZE_PURCHASED,
    // PR_REVOKED, DEBT_SETTLED

    @Column(name = "reference_type")
    private String referenceType;
    // PR_EVENT, SESSION, WEEK, PURCHASE

    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;

    @Column(name = "debt_before", nullable = false)
    private Integer debtBefore = 0;

    @Column(name = "debt_after", nullable = false)
    private Integer debtAfter = 0;

    @Column(name = "clamped_amount", nullable = false)
    private Integer clampedAmount = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public CoinTransaction() {}

    public UUID getId()              { return id; }

    public UUID getUserId()          { return userId; }
    public void setUserId(UUID v)    { this.userId = v; }

    public Integer getAmount()       { return amount; }
    public void setAmount(Integer v) { this.amount = v; }

    public String getDirection()     { return direction; }
    public void setDirection(String v){ this.direction = v; }

    public String getLabel()         { return label; }
    public void setLabel(String v)   { this.label = v; }

    public String getType()          { return type; }
    public void setType(String v)    { this.type = v; }

    public String getReferenceId()           { return referenceId; }
    public void setReferenceId(String v)     { this.referenceId = v; }

    public Integer getDelta()        { return delta; }
    public void setDelta(Integer v)  { this.delta = v; }

    public String getReason()        { return reason; }
    public void setReason(String v)  { this.reason = v; }

    public String getReferenceType()           { return referenceType; }
    public void setReferenceType(String v)     { this.referenceType = v; }

    public Integer getBalanceAfter()           { return balanceAfter; }
    public void setBalanceAfter(Integer v)     { this.balanceAfter = v; }

    public Integer getDebtBefore()             { return debtBefore; }
    public void setDebtBefore(Integer v)       { this.debtBefore = v; }

    public Integer getDebtAfter()              { return debtAfter; }
    public void setDebtAfter(Integer v)        { this.debtAfter = v; }

    public Integer getClampedAmount()          { return clampedAmount; }
    public void setClampedAmount(Integer v)    { this.clampedAmount = v; }

    public Instant getCreatedAt()    { return createdAt; }
}
