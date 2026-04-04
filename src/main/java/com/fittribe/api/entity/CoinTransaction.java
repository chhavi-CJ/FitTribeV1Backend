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

    public Instant getCreatedAt()    { return createdAt; }
}
