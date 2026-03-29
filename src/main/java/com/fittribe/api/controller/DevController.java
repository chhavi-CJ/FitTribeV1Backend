package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Dev-only endpoints. Every method checks that firebase.project-id
 * is still "placeholder" before doing anything — returns 404 otherwise.
 *
 * NEVER deploy with FIREBASE_PROJECT_ID unset in production.
 */
@ConditionalOnProperty(name = "firebase.project-id", havingValue = "placeholder")
@RestController
@RequestMapping("/api/v1/dev")
public class DevController {

    @Value("${firebase.project-id:placeholder}")
    private String firebaseProjectId;

    private final WorkoutSessionRepository sessionRepo;

    public DevController(WorkoutSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    /**
     * POST /api/v1/dev/clear-cooldown
     * Body: { "userId": "uuid" }
     *
     * Sets the last completed session's finished_at to 9 hours ago,
     * allowing a new session to start immediately.
     *
     * Only works when FIREBASE_PROJECT_ID = "placeholder" (dev/test mode).
     * Returns 404 in production.
     */
    @PostMapping("/clear-cooldown")
    @Transactional
    public ResponseEntity<ApiResponse<?>> clearCooldown(@RequestBody Map<String, String> body) {
        if (!"placeholder".equals(firebaseProjectId)) {
            return ResponseEntity.notFound().build();
        }

        String rawId = body.get("userId");
        if (rawId == null || rawId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("userId is required", "BAD_REQUEST"));
        }

        UUID userId;
        try {
            userId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("userId must be a valid UUID", "BAD_REQUEST"));
        }

        // Find the session actually causing the cooldown: finished within the last 8 hours
        WorkoutSession last = sessionRepo
                .findFirstByUserIdAndStatusAndFinishedAtAfter(
                        userId, "COMPLETED", Instant.now().minus(9, ChronoUnit.HOURS))
                .orElse(null);

        if (last == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("cleared", false, "reason", "No completed session found for this user")));
        }

        Instant nineHoursAgo = Instant.now().minus(9, ChronoUnit.HOURS);
        last.setFinishedAt(nineHoursAgo);
        sessionRepo.save(last);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "cleared",     true,
                "sessionId",   last.getId(),
                "finishedAt",  nineHoursAgo.toString(),
                "message",     "Cooldown cleared — new session can start immediately"
        )));
    }
}
