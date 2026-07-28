package com.fittribe.api.dto.response;

import java.time.LocalDate;

public record DailyEventCountRow(
        LocalDate eventDate,
        long eventCount
) {}
