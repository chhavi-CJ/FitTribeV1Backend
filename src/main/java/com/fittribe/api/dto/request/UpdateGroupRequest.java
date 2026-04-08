package com.fittribe.api.dto.request;

public record UpdateGroupRequest(
        String name,
        String icon,
        String color
) {}
