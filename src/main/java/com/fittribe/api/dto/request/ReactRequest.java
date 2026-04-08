package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReactRequest(
        @NotBlank
        @Pattern(regexp = "STRONG|RESPECT|KEEP_GOING|COMMENDABLE",
                 message = "type must be STRONG, RESPECT, KEEP_GOING, or COMMENDABLE")
        String type
) {}
