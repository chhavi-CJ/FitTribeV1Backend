package com.fittribe.api.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Column(name = "phone", unique = true)
    private String phone;

    @Column(name = "email", length = 320)
    private String email;

    @Column(name = "auth_provider", length = 20)
    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @Column(name = "auth_providers", length = 80)
    private String authProviders;

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

    /**
     * Count of freeze tokens the user has purchased with coins.
     * Persistent — never expires, no cap, never auto-zeroed.
     * Bonus freezes (earned by hitting weekly goal) live separately
     * in the bonus_freeze_grants table and have a 28-day expiry.
     */
    @Column(name = "purchased_freeze_balance", nullable = false)
    private Integer purchasedFreezeBalance = 0;

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

    @Column(name = "last_baseline_computed_at")
    private Instant lastBaselineComputedAt;

    @Column(name = "leaderboard_eligible", nullable = false)
    private Boolean leaderboardEligible = true;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Kolkata";

    @Column(name = "pause_until")
    private Instant pauseUntil;

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

    public String getEmail()               { return email; }
    public void setEmail(String email)     { this.email = email; }

    public AuthProvider getAuthProvider()                    { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider)   { this.authProvider = authProvider; }

    public String getAuthProviders()                       { return authProviders; }
    public void setAuthProviders(String authProviders)     { this.authProviders = authProviders; }

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

    public Integer getPurchasedFreezeBalance()                             { return purchasedFreezeBalance; }
    public void setPurchasedFreezeBalance(Integer purchasedFreezeBalance)  { this.purchasedFreezeBalance = purchasedFreezeBalance; }

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

    public Instant getLastBaselineComputedAt()                               { return lastBaselineComputedAt; }
    public void setLastBaselineComputedAt(Instant lastBaselineComputedAt)   { this.lastBaselineComputedAt = lastBaselineComputedAt; }

    public Boolean getLeaderboardEligible()                                  { return leaderboardEligible; }
    public void setLeaderboardEligible(Boolean leaderboardEligible)         { this.leaderboardEligible = leaderboardEligible; }

    public String getTimezone()                                              { return timezone; }
    public void setTimezone(String timezone)                                 { this.timezone = timezone; }

    public Instant getPauseUntil()                                           { return pauseUntil; }
    public void setPauseUntil(Instant pauseUntil)                           { this.pauseUntil = pauseUntil; }

    public Instant getCreatedAt()    { return createdAt; }

    public Set<AuthProvider> getLinkedProviders() {
        if (authProviders == null || authProviders.isBlank()) return EnumSet.noneOf(AuthProvider.class);
        return Arrays.stream(authProviders.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(s -> {
                    try {
                        return java.util.stream.Stream.of(AuthProvider.valueOf(s));
                    } catch (IllegalArgumentException ignored) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AuthProvider.class)));
    }

    public void linkProvider(AuthProvider provider) {
        Set<AuthProvider> current = getLinkedProviders();
        current.add(provider);
        this.authProviders = current.stream()
                .map(AuthProvider::name)
                .sorted()
                .collect(Collectors.joining(","));
    }
}
