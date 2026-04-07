package com.fittribe.api.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_routines")
public class SavedRoutine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "exercises", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String exercises;

    @Column(name = "times_used")
    private Integer timesUsed = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // ── Required by JPA ───────────────────────────────────────────────
    public SavedRoutine() {}

    // ── Getters / Setters ─────────────────────────────────────────────
    public UUID getId()                          { return id; }

    public UUID getUserId()                      { return userId; }
    public void setUserId(UUID v)                { this.userId = v; }

    public String getName()                      { return name; }
    public void setName(String v)                { this.name = v; }

    public String getExercises()                 { return exercises; }
    public void setExercises(String v)           { this.exercises = v; }

    public Integer getTimesUsed()                { return timesUsed; }
    public void setTimesUsed(Integer v)          { this.timesUsed = v; }

    public Instant getLastUsedAt()               { return lastUsedAt; }
    public void setLastUsedAt(Instant v)         { this.lastUsedAt = v; }

    public Instant getCreatedAt()                { return createdAt; }
    public void setCreatedAt(Instant v)          { this.createdAt = v; }

    public Instant getUpdatedAt()                { return updatedAt; }
    public void setUpdatedAt(Instant v)          { this.updatedAt = v; }
}
