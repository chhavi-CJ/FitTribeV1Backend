package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final WorkoutSessionRepository sessionRepo;

    public AiController(WorkoutSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    // ── GET /ai/insight/{sessionId} ───────────────────────────────────
    @GetMapping("/insight/{sessionId}")
    public ResponseEntity<ApiResponse<?>> getInsight(
            @PathVariable UUID sessionId,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();

        WorkoutSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session"));

        if (!session.getUserId().equals(userId)) {
            throw ApiException.forbidden();
        }

        if (session.getAiInsight() != null) {
            return ResponseEntity.ok(ApiResponse.success(Map.of("insight", session.getAiInsight())));
        }

        // Still generating
        return ResponseEntity.noContent().build();
    }
}
