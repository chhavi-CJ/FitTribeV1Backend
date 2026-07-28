package com.fittribe.api.dto.response;

import java.time.LocalDate;

public record RetentionCohortRow(
        LocalDate cohortWeek,
        long cohortSize,
        long retainedWeek1,
        long retainedWeek2,
        long retainedWeek3,
        long retainedWeek4,
        double retentionWeek1Percent,
        double retentionWeek2Percent,
        double retentionWeek3Percent,
        double retentionWeek4Percent
) {}
