package com.fittribe.api.dto.request;

import java.util.Map;

public record TrackEventRequest(String eventName, Map<String, Object> properties) {}
