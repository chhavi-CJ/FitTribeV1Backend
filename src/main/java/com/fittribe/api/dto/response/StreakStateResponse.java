package com.fittribe.api.dto.response;

public record StreakStateResponse(
        int streak,
        int maxStreakEver,
        ThisWeekSummary thisWeek,
        FreezesSummary freezes,
        String projectedStatus,  // "SAFE", "AT_RISK", "WILL_BREAK"
        boolean autoFreezeEnabled
) {}
