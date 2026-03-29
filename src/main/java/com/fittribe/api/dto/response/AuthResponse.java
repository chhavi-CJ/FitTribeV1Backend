package com.fittribe.api.dto.response;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        boolean isNewUser,
        String displayName
) {}
