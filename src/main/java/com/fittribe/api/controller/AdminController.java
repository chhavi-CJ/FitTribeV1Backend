package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.repository.DeviceTokenRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * General admin endpoints for monitoring and system health.
 * Routes: /api/v1/admin/**
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final DeviceTokenRepository deviceTokenRepo;
    private final UserRepository userRepo;

    public AdminController(DeviceTokenRepository deviceTokenRepo, UserRepository userRepo) {
        this.deviceTokenRepo = deviceTokenRepo;
        this.userRepo = userRepo;
    }

    // ── GET /api/v1/admin/notification-health ───────────────────────────
    @GetMapping("/notification-health")
    public ResponseEntity<?> getNotificationHealth() {
        try {
            Map<String, Object> health = new HashMap<>();

            // Device token counts by platform
            long iosTokens = deviceTokenRepo.countByPlatform("IOS");
            long androidTokens = deviceTokenRepo.countByPlatform("ANDROID");
            long webTokens = deviceTokenRepo.countByPlatform("WEB");
            long totalTokens = iosTokens + androidTokens + webTokens;

            health.put("deviceTokens", Map.of(
                    "ios", iosTokens,
                    "android", androidTokens,
                    "web", webTokens,
                    "total", totalTokens
            ));

            // Total registered users vs users with tokens
            long totalUsers = userRepo.count();
            long usersWithTokens = deviceTokenRepo.countDistinctUserId();
            long coverage = totalUsers > 0 ? (usersWithTokens * 100 / totalUsers) : 0;

            health.put("coverage", Map.of(
                    "totalUsers", totalUsers,
                    "usersWithTokens", usersWithTokens,
                    "coveragePercent", coverage
            ));

            log.info("Notification health: {} total tokens, {} users with tokens ({} coverage)",
                    totalTokens, usersWithTokens, coverage + "%");

            return ResponseEntity.ok(Map.of("data", health));
        } catch (Exception e) {
            log.error("Error fetching notification health", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", Map.of("message", "Failed to fetch notification health")));
        }
    }
}
