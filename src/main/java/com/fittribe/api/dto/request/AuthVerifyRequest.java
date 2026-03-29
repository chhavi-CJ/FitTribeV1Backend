package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AuthVerifyRequest(
        @NotBlank(message = "idToken is required") String idToken
) {}
