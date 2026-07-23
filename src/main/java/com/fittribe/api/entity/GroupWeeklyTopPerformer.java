package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_weekly_top_performer")
public class GroupWeeklyTopPerformer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "iso_year", nullable = false)
    private int isoYear;

    @Column(name = "iso_week", nullable = false)
    private int isoWeek;

    @Column(name = "winner_user_id", nullable = false)
    private UUID winnerUserId;

    @Column(name = "dimension", nullable = false, length = 20)
    private String dimension;

    @Column(name = "score_value", nullable = false)
    private int scoreValue;

    @Column(name = "metric_label")
    private String metricLabel;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public GroupWeeklyTopPerformer() {}

    public UUID getId()                         { return id; }

    public UUID getGroupId()                    { return groupId; }
    public void setGroupId(UUID v)              { this.groupId = v; }

    public int getIsoYear()                     { return isoYear; }
    public void setIsoYear(int v)               { this.isoYear = v; }

    public int getIsoWeek()                     { return isoWeek; }
    public void setIsoWeek(int v)               { this.isoWeek = v; }

    public UUID getWinnerUserId()               { return winnerUserId; }
    public void setWinnerUserId(UUID v)         { this.winnerUserId = v; }

    public String getDimension()                { return dimension; }
    public void setDimension(String v)          { this.dimension = v; }

    public int getScoreValue()                  { return scoreValue; }
    public void setScoreValue(int v)            { this.scoreValue = v; }

    public String getMetricLabel()              { return metricLabel; }
    public void setMetricLabel(String v)        { this.metricLabel = v; }

    public Instant getCreatedAt()               { return createdAt; }
}
