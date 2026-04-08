package com.fittribe.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String displayName,
        String fitnessLevel,
        String primaryGoal,
        BigDecimal currentWeightKg,
        int weeklyGoal,
        Integer pendingWeeklyGoal,
        int completedThisWeek,
        int streak,
        int maxStreakEver,
        int sessionsTotal,
        int prsTotal,
        int fitcoinsBalance,
        int streakFreezeBalance,
        boolean autoFreezeEnabled,
        String rank,
        boolean notificationsEnabled,
        boolean showInLeaderboard,
        String weightUnit
) {}
