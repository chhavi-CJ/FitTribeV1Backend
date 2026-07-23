package com.fittribe.api.dto.response;

import java.time.Instant;
import java.util.Map;

public record HistoryEvent(
        String eventType,
        int amount,
        Instant occurredAt,
        Map<String, Object> metadata
) {}
