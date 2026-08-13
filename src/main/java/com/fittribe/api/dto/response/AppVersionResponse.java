package com.fittribe.api.dto.response;

public record AppVersionResponse(
        PlatformVersionInfo ios,
        PlatformVersionInfo android,
        String iosStoreUrl,
        String androidStoreUrl
) {
    public record PlatformVersionInfo(
            String minVersion,
            String latestVersion
    ) {}
}
