package com.fittribe.api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;

    @Column(name = "phone", unique = true, nullable = false)
    private String phone;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "gender")
    private String gender;

    @Column(name = "goal")
    private String goal;

    @Column(name = "fitness_level")
    private String fitnessLevel;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Column(name = "weekly_goal")
    private Integer weeklyGoal = 4;

    @Column(name = "pending_weekly_goal")
    private Integer pendingWeeklyGoal;

    @Column(name = "weight_unit", nullable = false)
    private String weightUnit = "KG";

    @Column(name = "coins")
    private Integer coins = 0;

    @Column(name = "streak")
    private Integer streak = 0;

    @Column(name = "rank", nullable = false)
    private String rank = "ROOKIE";

    @Column(name = "max_streak_ever", nullable = false)
    private Integer maxStreakEver = 0;

    @Column(name = "health_conditions", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] healthConditions = new String[0];

    @Column(name = "streak_freeze_balance", nullable = false)
    private Integer streakFreezeBalance = 0;

    @Column(name = "auto_freeze_enabled", nullable = false)
    private Boolean autoFreezeEnabled = true;

    @Column(name = "notifications_enabled", nullable = false)
    private Boolean notificationsEnabled = true;

    @Column(name = "show_in_leaderboard", nullable = false)
    private Boolean showInLeaderboard = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "ai_context", length = 500)
    private String aiContext;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // ── Required by JPA ──────────────────────────────────────────────
    public User() {}

    // ── Getters / Setters ────────────────────────────────────────────
    public UUID getId()                    { return id; }
    public void setId(UUID id)             { this.id = id; }

    public String getFirebaseUid()                   { return firebaseUid; }
    public void setFirebaseUid(String firebaseUid)   { this.firebaseUid = firebaseUid; }

    public String getPhone()                   { return phone; }
    public void setPhone(String phone)         { this.phone = phone; }

    public String getDisplayName()                     { return displayName; }
    public void setDisplayName(String displayName)     { this.displayName = displayName; }

    public String getGender()                  { return gender; }
    public void setGender(String gender)       { this.gender = gender; }

    public String getGoal()                { return goal; }
    public void setGoal(String goal)       { this.goal = goal; }

    public String getFitnessLevel()                      { return fitnessLevel; }
    public void setFitnessLevel(String fitnessLevel)     { this.fitnessLevel = fitnessLevel; }

    public BigDecimal getWeightKg()                    { return weightKg; }
    public void setWeightKg(BigDecimal weightKg)       { this.weightKg = weightKg; }

    public BigDecimal getHeightCm()                    { return heightCm; }
    public void setHeightCm(BigDecimal heightCm)       { this.heightCm = heightCm; }

    public Integer getWeeklyGoal()                     { return weeklyGoal; }
    public void setWeeklyGoal(Integer weeklyGoal)      { this.weeklyGoal = weeklyGoal; }

    public Integer getPendingWeeklyGoal()                        { return pendingWeeklyGoal; }
    public void setPendingWeeklyGoal(Integer pendingWeeklyGoal) { this.pendingWeeklyGoal = pendingWeeklyGoal; }

    public String getWeightUnit()                  { return weightUnit; }
    public void setWeightUnit(String weightUnit)   { this.weightUnit = weightUnit; }

    public Integer getCoins()              { return coins; }
    public void setCoins(Integer coins)    { this.coins = coins; }

    public Integer getStreak()               { return streak; }
    public void setStreak(Integer streak)    { this.streak = streak; }

    public String getRank()                  { return rank; }
    public void setRank(String rank)         { this.rank = rank; }

    public Integer getMaxStreakEver()                      { return maxStreakEver; }
    public void setMaxStreakEver(Integer maxStreakEver)    { this.maxStreakEver = maxStreakEver; }

    public String[] getHealthConditions()                        { return healthConditions; }
    public void setHealthConditions(String[] healthConditions)   { this.healthConditions = healthConditions; }

    public Integer getStreakFreezeBalance()                          { return streakFreezeBalance; }
    public void setStreakFreezeBalance(Integer streakFreezeBalance)  { this.streakFreezeBalance = streakFreezeBalance; }

    public Boolean getAutoFreezeEnabled()                              { return autoFreezeEnabled; }
    public void setAutoFreezeEnabled(Boolean autoFreezeEnabled)      { this.autoFreezeEnabled = autoFreezeEnabled; }

    public Boolean getNotificationsEnabled()                           { return notificationsEnabled; }
    public void setNotificationsEnabled(Boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public Boolean getShowInLeaderboard()                        { return showInLeaderboard; }
    public void setShowInLeaderboard(Boolean showInLeaderboard)  { this.showInLeaderboard = showInLeaderboard; }

    public Boolean getIsActive()               { return isActive; }
    public void setIsActive(Boolean isActive)  { this.isActive = isActive; }

    public Instant getDeletionRequestedAt()                         { return deletionRequestedAt; }
    public void setDeletionRequestedAt(Instant deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }

    public String getAiContext()               { return aiContext; }
    public void setAiContext(String aiContext) { this.aiContext = aiContext; }

    public Instant getCreatedAt()    { return createdAt; }
}
