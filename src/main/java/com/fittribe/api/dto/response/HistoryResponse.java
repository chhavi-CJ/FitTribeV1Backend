package com.fittribe.api.dto.response;

import java.util.List;

public record HistoryResponse(
        List<HistoryEvent> events,
        int returnedCount
) {}
