package com.fittribe.api.dto.group;

import java.util.UUID;

public class TopPerformerDto {

    private UUID   winnerUserId;
    private String winnerDisplayName;
    private String winnerAvatarInitials;
    private String dimension;
    private int    scoreValue;
    private String metricLabel;
    private int    isoYear;
    private int    isoWeek;
    private boolean isCurrentUser;

    public TopPerformerDto() {}

    public UUID getWinnerUserId()                       { return winnerUserId; }
    public void setWinnerUserId(UUID v)                 { this.winnerUserId = v; }

    public String getWinnerDisplayName()                { return winnerDisplayName; }
    public void setWinnerDisplayName(String v)          { this.winnerDisplayName = v; }

    public String getWinnerAvatarInitials()             { return winnerAvatarInitials; }
    public void setWinnerAvatarInitials(String v)       { this.winnerAvatarInitials = v; }

    public String getDimension()                        { return dimension; }
    public void setDimension(String v)                  { this.dimension = v; }

    public int getScoreValue()                          { return scoreValue; }
    public void setScoreValue(int v)                    { this.scoreValue = v; }

    public String getMetricLabel()                      { return metricLabel; }
    public void setMetricLabel(String v)                { this.metricLabel = v; }

    public int getIsoYear()                             { return isoYear; }
    public void setIsoYear(int v)                       { this.isoYear = v; }

    public int getIsoWeek()                             { return isoWeek; }
    public void setIsoWeek(int v)                       { this.isoWeek = v; }

    public boolean isCurrentUser()                      { return isCurrentUser; }
    public void setCurrentUser(boolean v)               { this.isCurrentUser = v; }
}
