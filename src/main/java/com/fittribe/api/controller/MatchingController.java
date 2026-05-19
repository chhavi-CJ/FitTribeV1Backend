package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.matching.MatchingApiService;
import com.fittribe.api.matching.dto.MatchingDtos.SubmitQuizRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * User-facing Conscious Matching API. Thin controller — all logic and
 * state-transition rules live in {@link MatchingApiService}. Errors are
 * thrown as {@code ApiException} and rendered by GlobalExceptionHandler.
 *
 * <p>Auth: standard JWT. The authenticated user id is the
 * {@link Authentication} principal — same convention as the other
 * user-facing controllers (e.g. NotificationController).
 */
@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    private final MatchingApiService matchingApiService;

    public MatchingController(MatchingApiService matchingApiService) {
        this.matchingApiService = matchingApiService;
    }

    // ── POST /submit-quiz ─────────────────────────────────────────────
    @PostMapping("/submit-quiz")
    public ResponseEntity<ApiResponse<?>> submitQuiz(
            @RequestBody SubmitQuizRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                matchingApiService.submitQuiz(userId(auth), req)));
    }

    // ── GET /me ───────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> me(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                matchingApiService.getMyStatus(userId(auth))));
    }

    // ── POST /opt-out ─────────────────────────────────────────────────
    @PostMapping("/opt-out")
    public ResponseEntity<ApiResponse<?>> optOut(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                matchingApiService.optOut(userId(auth))));
    }

    // ── POST /rejoin ──────────────────────────────────────────────────
    @PostMapping("/rejoin")
    public ResponseEntity<ApiResponse<?>> rejoin(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(
                matchingApiService.rejoin(userId(auth))));
    }

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }
}
