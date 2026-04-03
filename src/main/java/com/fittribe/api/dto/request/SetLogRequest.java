package com.fittribe.api.dto.request;

import java.math.BigDecimal;

public record SetLogRequest(
        int setNumber,
        int reps,
        BigDecimal weightKg
) {}
