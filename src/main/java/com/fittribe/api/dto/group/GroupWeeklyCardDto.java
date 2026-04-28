package com.fittribe.api.dto.group;

import java.time.Instant;
import java.util.UUID;

public record GroupWeeklyCardDto(
        UUID    id,
        UUID    groupId,
        int     isoYear,
        int     isoWeek,
        String  finalTier,
        int     sessionsLogged,
        int     targetSessions,
        int     finalPercentage,
        boolean overachiever,
        int     streakAtLock,
        UUID[]  contributorUserIds,
        Instant lockedAt
) {
    public static GroupWeeklyCardDto from(com.fittribe.api.entity.GroupWeeklyCard c) {
        return new GroupWeeklyCardDto(
                c.getId(), c.getGroupId(),
                c.getIsoYear(), c.getIsoWeek(),
                c.getFinalTier(),
                c.getSessionsLogged(), c.getTargetSessions(), c.getFinalPercentage(),
                c.isOverachiever(), c.getStreakAtLock(),
                c.getContributorUserIds(), c.getLockedAt());
    }
}
