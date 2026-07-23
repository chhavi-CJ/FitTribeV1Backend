package com.fittribe.api.dto.response;

import java.time.LocalDate;

public record ThisWeekSummary(
        LocalDate weekStart,
        int completed,
        int goal,
        int shortfall,
        int daysRemainingInWeek
) {}
