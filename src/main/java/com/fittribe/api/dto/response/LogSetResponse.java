package com.fittribe.api.dto.response;

import java.util.UUID;

public record LogSetResponse(UUID setId, boolean isPr, PrDetails prDetails) {}
