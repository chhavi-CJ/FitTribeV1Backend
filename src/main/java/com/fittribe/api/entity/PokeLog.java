package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pokes_log")
public class PokeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "poker_user_id", nullable = false)
    private UUID pokerUserId;

    @Column(name = "poked_at", insertable = false, updatable = false)
    private Instant pokedAt;

    @Column(name = "poked_date", nullable = false)
    private LocalDate pokedDate;

    public PokeLog() {}

    public UUID getId()                       { return id; }
    public UUID getGroupId()                  { return groupId; }
    public void setGroupId(UUID v)            { this.groupId = v; }
    public UUID getRecipientUserId()          { return recipientUserId; }
    public void setRecipientUserId(UUID v)    { this.recipientUserId = v; }
    public UUID getPokerUserId()              { return pokerUserId; }
    public void setPokerUserId(UUID v)        { this.pokerUserId = v; }
    public Instant getPokedAt()               { return pokedAt; }
    public LocalDate getPokedDate()           { return pokedDate; }
    public void setPokedDate(LocalDate v)     { this.pokedDate = v; }
}
