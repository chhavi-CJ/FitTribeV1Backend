package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.response.AppVersionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @Value("${app-version.min-version-ios:1.0.0}")
    private String minVersionIos;

    @Value("${app-version.latest-version-ios:1.0.0}")
    private String latestVersionIos;

    @Value("${app-version.min-version-android:1.0.0}")
    private String minVersionAndroid;

    @Value("${app-version.latest-version-android:1.0.0}")
    private String latestVersionAndroid;

    @Value("${app-version.store-url-ios:https://apps.apple.com/app/fittribe}")
    private String storeUrlIos;

    @Value("${app-version.store-url-android:https://play.google.com/store/apps/details?id=com.fittribe}")
    private String storeUrlAndroid;

    @GetMapping("/app-version")
    public ResponseEntity<ApiResponse<?>> getAppVersion() {
        AppVersionResponse response = new AppVersionResponse(
                new AppVersionResponse.PlatformVersionInfo(minVersionIos, latestVersionIos),
                new AppVersionResponse.PlatformVersionInfo(minVersionAndroid, latestVersionAndroid),
                storeUrlIos,
                storeUrlAndroid
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
