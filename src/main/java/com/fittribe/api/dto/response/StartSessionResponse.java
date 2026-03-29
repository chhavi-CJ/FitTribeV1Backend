package com.fittribe.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StartSessionResponse(UUID sessionId, Instant startedAt) {}
