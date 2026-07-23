package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "streak_enabled", nullable = false)
    private Boolean streakEnabled = true;

    @Column(name = "group_activity_enabled", nullable = false)
    private Boolean groupActivityEnabled = true;

    @Column(name = "weekly_report_enabled", nullable = false)
    private Boolean weeklyReportEnabled = true;

    @Column(name = "social_enabled", nullable = false)
    private Boolean socialEnabled = true;

    @Column(name = "comeback_enabled", nullable = false)
    private Boolean comebackEnabled = true;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_start", nullable = false)
    private LocalTime quietStart = LocalTime.of(22, 0);

    @Column(name = "quiet_end", nullable = false)
    private LocalTime quietEnd = LocalTime.of(7, 0);

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public NotificationPreference() {}

    public NotificationPreference(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId()                        { return userId; }
    public void setUserId(UUID v)                  { this.userId = v; }

    public Boolean getStreakEnabled()              { return streakEnabled; }
    public void setStreakEnabled(Boolean v)        { this.streakEnabled = v; }

    public Boolean getGroupActivityEnabled()       { return groupActivityEnabled; }
    public void setGroupActivityEnabled(Boolean v) { this.groupActivityEnabled = v; }

    public Boolean getWeeklyReportEnabled()        { return weeklyReportEnabled; }
    public void setWeeklyReportEnabled(Boolean v)  { this.weeklyReportEnabled = v; }

    public Boolean getSocialEnabled()              { return socialEnabled; }
    public void setSocialEnabled(Boolean v)        { this.socialEnabled = v; }

    public Boolean getComebackEnabled()            { return comebackEnabled; }
    public void setComebackEnabled(Boolean v)      { this.comebackEnabled = v; }

    public Boolean getQuietHoursEnabled()          { return quietHoursEnabled; }
    public void setQuietHoursEnabled(Boolean v)    { this.quietHoursEnabled = v; }

    public LocalTime getQuietStart()               { return quietStart; }
    public void setQuietStart(LocalTime v)         { this.quietStart = v; }

    public LocalTime getQuietEnd()                 { return quietEnd; }
    public void setQuietEnd(LocalTime v)           { this.quietEnd = v; }

    public Instant getCreatedAt()                  { return createdAt; }
    public Instant getUpdatedAt()                  { return updatedAt; }
}
