package com.fittribe.api.dto.group;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class GroupWeeklyProgressDto {

    private UUID groupId;
    private int isoYear;
    private int isoWeek;
    private int targetSessions;
    private int sessionsLogged;
    private String currentTier;
    private int percentComplete;
    private boolean overachiever;
    private Instant lockedAt;
    private List<MemberProgressDto> memberBreakdown;
    private MemberProgressDto myPersonalStatus;
    private int goldStreakIncludingThisWeek = 0;

    public GroupWeeklyProgressDto(
            UUID groupId,
            int isoYear,
            int isoWeek,
            int targetSessions,
            int sessionsLogged,
            String currentTier,
            int percentComplete,
            boolean overachiever,
            Instant lockedAt,
            List<MemberProgressDto> memberBreakdown,
            MemberProgressDto myPersonalStatus) {
        this.groupId = groupId;
        this.isoYear = isoYear;
        this.isoWeek = isoWeek;
        this.targetSessions = targetSessions;
        this.sessionsLogged = sessionsLogged;
        this.currentTier = currentTier;
        this.percentComplete = percentComplete;
        this.overachiever = overachiever;
        this.lockedAt = lockedAt;
        this.memberBreakdown = memberBreakdown;
        this.myPersonalStatus = myPersonalStatus;
    }

    public UUID getGroupId()                          { return groupId; }
    public int getIsoYear()                           { return isoYear; }
    public int getIsoWeek()                           { return isoWeek; }
    public int getTargetSessions()                    { return targetSessions; }
    public int getSessionsLogged()                    { return sessionsLogged; }
    public String getCurrentTier()                    { return currentTier; }
    public int getPercentComplete()                   { return percentComplete; }
    public boolean isOverachiever()                   { return overachiever; }
    public Instant getLockedAt()                      { return lockedAt; }
    public List<MemberProgressDto> getMemberBreakdown() { return memberBreakdown; }
    public MemberProgressDto getMyPersonalStatus()    { return myPersonalStatus; }
    public int getGoldStreakIncludingThisWeek()        { return goldStreakIncludingThisWeek; }

    public void setGoldStreakIncludingThisWeek(int v) { this.goldStreakIncludingThisWeek = v; }
}
