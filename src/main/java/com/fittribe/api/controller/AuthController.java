package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.AuthVerifyRequest;
import com.fittribe.api.dto.response.AuthResponse;
import com.fittribe.api.service.AuthRateLimiter;
import com.fittribe.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService    authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService  = authService;
        this.rateLimiter  = rateLimiter;
    }

    /**
     * POST /api/v1/auth/verify-firebase
     * Body: { "idToken": "<Firebase ID token or mock_ token>" }
     *
     * Accepts Firebase ID tokens from any supported provider:
     * phone OTP, email/password, Google, Apple.
     *
     * Mock mode (development only, app.mock-auth-enabled=true):
     *   "mock_+919876543210"       → phone provider
     *   "mock_email_user@x.com"   → email provider
     *   "mock_google_user@x.com"  → Google provider
     *   "mock_apple_user@x.com"   → Apple provider
     */
    @PostMapping("/verify-firebase")
    public ResponseEntity<ApiResponse<?>> verifyFirebase(
            @RequestBody @Valid AuthVerifyRequest request,
            HttpServletRequest httpRequest) {

        String clientIp = extractIp(httpRequest);
        if (!rateLimiter.tryConsume(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(ApiResponse.error("Too many requests. Try again in 1 minute.", "RATE_LIMITED"));
        }

        AuthResponse authResponse = authService.verifyFirebaseToken(request.idToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
