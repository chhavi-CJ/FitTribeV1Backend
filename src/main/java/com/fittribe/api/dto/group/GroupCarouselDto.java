package com.fittribe.api.dto.group;

import java.util.UUID;

public record GroupCarouselDto(
        UUID   groupId,
        String groupName,
        String groupIcon,
        int    isoYear,
        int    isoWeek,
        String currentTier,
        int    sessionsLogged,
        int    targetSessions,
        int    percentComplete,
        int    mySessionsContributed
) {}
