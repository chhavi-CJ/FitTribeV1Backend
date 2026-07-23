package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateSessionRequest(
        @NotNull Instant startedAt,
        @NotNull Instant finishedAt
) {}
