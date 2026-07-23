package com.fittribe.api.dto.group;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class LeaderboardEntryDto {

    private UUID    userId;
    private String  displayName;
    private String  avatarInitials;
    /** Null for improvement-board entries that are unranked (NEW_MEMBER / GOAL_NOT_HIT). */
    private Integer score;
    /** Null for improvement-board entries that are unranked. */
    private Integer rank;
    @JsonProperty("isCurrentUser")
    private boolean isCurrentUser;
    /** Null for normally-ranked entries (spec: "null — normal ranking"). */
    private String  status;
    private String  displaySuffix;
    private String  scoreDisplay;

    public LeaderboardEntryDto() {}

    public UUID    getUserId()               { return userId; }
    public void    setUserId(UUID v)         { this.userId = v; }

    public String  getDisplayName()          { return displayName; }
    public void    setDisplayName(String v)  { this.displayName = v; }

    public String  getAvatarInitials()           { return avatarInitials; }
    public void    setAvatarInitials(String v)   { this.avatarInitials = v; }

    public Integer getScore()                { return score; }
    public void    setScore(Integer v)       { this.score = v; }

    public Integer getRank()                 { return rank; }
    public void    setRank(Integer v)        { this.rank = v; }

    @JsonProperty("isCurrentUser")
    public boolean isCurrentUser()               { return isCurrentUser; }
    public void    setCurrentUser(boolean v)     { this.isCurrentUser = v; }

    public String  getStatus()               { return status; }
    public void    setStatus(String v)       { this.status = v; }

    public String  getDisplaySuffix()            { return displaySuffix; }
    public void    setDisplaySuffix(String v)    { this.displaySuffix = v; }

    public String  getScoreDisplay()             { return scoreDisplay; }
    public void    setScoreDisplay(String v)     { this.scoreDisplay = v; }
}
