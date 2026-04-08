package com.fittribe.api.dto.response;

import java.time.Instant;

public record FeedbackInfo(String rating, String notes, Instant createdAt) {}
