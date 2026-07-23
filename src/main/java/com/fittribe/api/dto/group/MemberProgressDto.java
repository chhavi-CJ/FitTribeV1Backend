package com.fittribe.api.dto.group;

import java.util.UUID;

public record MemberProgressDto(
        UUID    userId,
        String  displayName,
        int     weeklyGoal,
        int     sessionsContributed,
        boolean isActive,
        boolean joinedThisWeek,
        boolean leftThisWeek
) {}
