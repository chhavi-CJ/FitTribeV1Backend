package com.fittribe.api.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matching_profile")
public class MatchingProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "answer_q1", length = 64, nullable = false)
    private String answerQ1;

    @Column(name = "answer_q2", length = 64, nullable = false)
    private String answerQ2;

    @Column(name = "answer_q3", length = 64, nullable = false)
    private String answerQ3;

    @Column(name = "answer_q4", length = 64, nullable = false)
    private String answerQ4;

    @Column(name = "score_q1", nullable = false)
    private int scoreQ1;

    @Column(name = "score_q2", nullable = false)
    private int scoreQ2;

    @Column(name = "score_q3", nullable = false)
    private int scoreQ3;

    @Column(name = "score_q4", nullable = false)
    private int scoreQ4;

    @Enumerated(EnumType.STRING)
    @Column(name = "archetype", length = 32, nullable = false)
    private Archetype archetype;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_gender_pref", length = 8, nullable = false)
    private PartnerGenderPref partnerGenderPref = PartnerGenderPref.ANY;

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
    public MatchingProfile() {}

    // ── Getters / Setters ─────────────────────────────────────────────
    public UUID getId()                              { return id; }

    public UUID getUserId()                          { return userId; }
    public void setUserId(UUID v)                    { this.userId = v; }

    public String getAnswerQ1()                      { return answerQ1; }
    public void setAnswerQ1(String v)                { this.answerQ1 = v; }

    public String getAnswerQ2()                      { return answerQ2; }
    public void setAnswerQ2(String v)                { this.answerQ2 = v; }

    public String getAnswerQ3()                      { return answerQ3; }
    public void setAnswerQ3(String v)                { this.answerQ3 = v; }

    public String getAnswerQ4()                      { return answerQ4; }
    public void setAnswerQ4(String v)                { this.answerQ4 = v; }

    public int getScoreQ1()                          { return scoreQ1; }
    public void setScoreQ1(int v)                    { this.scoreQ1 = v; }

    public int getScoreQ2()                          { return scoreQ2; }
    public void setScoreQ2(int v)                    { this.scoreQ2 = v; }

    public int getScoreQ3()                          { return scoreQ3; }
    public void setScoreQ3(int v)                    { this.scoreQ3 = v; }

    public int getScoreQ4()                          { return scoreQ4; }
    public void setScoreQ4(int v)                    { this.scoreQ4 = v; }

    public Archetype getArchetype()                  { return archetype; }
    public void setArchetype(Archetype v)            { this.archetype = v; }

    public PartnerGenderPref getPartnerGenderPref()        { return partnerGenderPref; }
    public void setPartnerGenderPref(PartnerGenderPref v)  { this.partnerGenderPref = v; }

    public Instant getCreatedAt()                    { return createdAt; }
    public Instant getUpdatedAt()                    { return updatedAt; }
}
