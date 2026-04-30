package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Temporary endpoint for verifying the FCM send pipeline end-to-end.
 * Requires both a valid user JWT and the X-Admin-Secret header.
 * Remove once real event triggers are wired.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminPushTestController {

    private static final Logger log = LoggerFactory.getLogger(AdminPushTestController.class);

    private final NotificationService notificationService;
    private final String configuredSecret;

    public AdminPushTestController(NotificationService notificationService,
                                   @Value("${fittribe.admin.secret:}") String configuredSecret) {
        this.notificationService = notificationService;
        this.configuredSecret    = configuredSecret;
    }

    // ── POST /admin/test-push ─────────────────────────────────────────
    @PostMapping("/test-push")
    public ResponseEntity<ApiResponse<?>> testPush(
            @RequestHeader(value = "X-Admin-Secret", required = false) String providedSecret,
            Authentication auth) {

        requireAdminSecret(providedSecret);
        UUID userId = (UUID) auth.getPrincipal();
        log.info("test-push: firing for userId={}", userId);
        notificationService.sendPush(userId, "Test push", "It works", Map.of());
        return ResponseEntity.ok(ApiResponse.success(Map.of("sent", true)));
    }

    private void requireAdminSecret(String provided) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("test-push rejected — fittribe.admin.secret not configured");
            throw ApiException.unauthorized();
        }
        if (provided == null || provided.isBlank() || !constantTimeEquals(configuredSecret, provided)) {
            throw ApiException.unauthorized();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
