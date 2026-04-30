package com.fittribe.api.dto.response;

import java.time.Instant;

public record BonusGrantSummary(Long id, Instant expiresAt) {}
