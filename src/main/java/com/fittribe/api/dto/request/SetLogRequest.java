package com.fittribe.api.dto.request;

import java.math.BigDecimal;

public record SetLogRequest(
        int setNumber,
        int reps,
        BigDecimal weightKg,
        Boolean isPr  // optional — frontend-supplied display-only optimistic flag.
                      // Persisted to JSONB without modification. The async PR
                      // pipeline computes authoritative isPr server-side and
                      // writes pr_events; coin/PR-write logic NEVER reads this field.
) {}
