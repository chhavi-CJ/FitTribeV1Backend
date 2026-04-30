package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.response.HistoryResponse;
import com.fittribe.api.dto.response.StreakStateResponse;
import com.fittribe.api.service.StreakStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/streak")
public class StreakController {

    private final StreakStateService streakStateService;

    public StreakController(StreakStateService streakStateService) {
        this.streakStateService = streakStateService;
    }

    // ── GET /api/v1/streak/state ──────────────────────────────────────
    @GetMapping("/state")
    public ResponseEntity<ApiResponse<StreakStateResponse>> getState(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(streakStateService.getState(userId)));
    }

    // ── GET /api/v1/streak/history ────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<HistoryResponse>> getHistory(
            Authentication auth,
            @RequestParam(required = false) Integer days) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.success(streakStateService.getHistory(userId, days)));
    }
}
