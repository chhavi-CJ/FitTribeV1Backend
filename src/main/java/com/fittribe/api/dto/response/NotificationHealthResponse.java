package com.fittribe.api.dto.response;

import java.time.Instant;

public record NotificationHealthResponse(
        long totalDeviceTokens,
        long iosTokens,
        long androidTokens,
        long webTokens,
        Instant queriedAt
) {}
