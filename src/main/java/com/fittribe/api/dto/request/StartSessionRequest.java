package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record StartSessionRequest(@NotBlank String name, String badge) {}
