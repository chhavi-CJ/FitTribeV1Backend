package com.fittribe.api.dto.response;

public record AnalyticsFunnelResponse(
        long signupCompleted,
        long onboardingCompleted,
        long firstWorkoutStarted,
        long firstWorkoutCompleted,
        long completedWithin48h,
        double conversionOnboardingPercent,
        double conversionFirstStartPercent,
        double conversionFirstCompletePercent,
        double conversionCompletedWithin48hPercent
) {}
