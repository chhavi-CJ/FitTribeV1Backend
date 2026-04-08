package com.fittribe.api.dto.request;

public record UpdateSettingsRequest(
        Boolean notificationsEnabled,
        Boolean showInLeaderboard,
        Boolean autoFreezeEnabled
) {}
