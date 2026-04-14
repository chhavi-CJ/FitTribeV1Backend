package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA mapping for {@code weekly_pr_counts} table (Flyway V44).
 *
 * <p>Pre-aggregated counters per user per week. Used for efficient display
 * of PR summaries and analytics, and for sealing weeks at the Sunday cron.
 * Not used as an economic gate — no weekly cap on PR rewards.
 *
 * <h3>Sealing semantics</h3>
 * {@code sealed_at} is set by the weekly cron at week end (Sunday 23:00 IST).
 * Once sealed, the row becomes read-only for normal flows. User edits cannot
 * cross the seal (bounded by the 5am window, well before seal time).
 */
@Entity
@Table(name = "weekly_pr_counts")
@IdClass(WeeklyPrCountId.class)
public class WeeklyPrCount {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "first_ever_count", nullable = false)
    private Integer firstEverCount = 0;

    @Column(name = "pr_count", nullable = false)
    private Integer prCount = 0;
    // Excludes first_ever and max_attempt

    @Column(name = "max_attempt_count", nullable = false)
    private Integer maxAttemptCount = 0;

    @Column(name = "total_coins_awarded", nullable = false)
    private Integer totalCoinsAwarded = 0;

    @Column(name = "sealed_at")
    private Instant sealedAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public WeeklyPrCount() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public Integer getFirstEverCount() { return firstEverCount; }
    public void setFirstEverCount(Integer firstEverCount) { this.firstEverCount = firstEverCount; }

    public Integer getPrCount() { return prCount; }
    public void setPrCount(Integer prCount) { this.prCount = prCount; }

    public Integer getMaxAttemptCount() { return maxAttemptCount; }
    public void setMaxAttemptCount(Integer maxAttemptCount) { this.maxAttemptCount = maxAttemptCount; }

    public Integer getTotalCoinsAwarded() { return totalCoinsAwarded; }
    public void setTotalCoinsAwarded(Integer totalCoinsAwarded) { this.totalCoinsAwarded = totalCoinsAwarded; }

    public Instant getSealedAt() { return sealedAt; }
    public void setSealedAt(Instant sealedAt) { this.sealedAt = sealedAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeeklyPrCount that = (WeeklyPrCount) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(weekStart, that.weekStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, weekStart);
    }
}
