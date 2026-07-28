package com.fittribe.api.dto.response;

public record AnalyticsOverviewResponse(
        long totalUsers,
        long newUsersToday,
        long newUsersThisWeek,
        long workoutsCompletedToday,
        long workoutsCompletedThisWeek,
        long dailyActiveUsers,
        long weeklyActiveUsers
) {}
