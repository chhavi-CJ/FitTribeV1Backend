package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "personal_records")
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "exercise_id", nullable = false)
    private String exerciseId;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(name = "reps", nullable = false)
    private Integer reps;

    @Column(name = "achieved_at", nullable = false)
    private Instant achievedAt;

    // ── Required by JPA ──────────────────────────────────────────────
    public PersonalRecord() {}

    // ── Getters ───────────────────────────────────────────────────────
    public Long getId()            { return id; }
    public UUID getUserId()        { return userId; }
    public String getExerciseId()  { return exerciseId; }
    public BigDecimal getWeightKg(){ return weightKg; }
    public Integer getReps()       { return reps; }
    public Instant getAchievedAt() { return achievedAt; }
}
