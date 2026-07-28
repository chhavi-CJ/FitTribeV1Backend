package com.fittribe.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ChurnRiskUserRow(
        UUID userId,
        String displayName,
        Instant lastWorkoutDate,
        int streakBeforeGap,
        String groupName
) {}
