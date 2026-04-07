package com.fittribe.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoutineResponse(
        UUID id,
        String name,
        List<Map<String, Object>> exercises,
        int timesUsed,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {}
