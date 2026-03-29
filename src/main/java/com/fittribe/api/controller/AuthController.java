package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.AuthVerifyRequest;
import com.fittribe.api.dto.response.AuthResponse;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.AuthRateLimiter;
import com.fittribe.api.service.AuthService;
import com.fittribe.api.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String MOCK_PREFIX = "mock_";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          JwtService jwtService,
                          AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * POST /api/v1/auth/verify-firebase
     * Body: { "idToken": "<Firebase ID token>" }
     *
     * Mock mode (development only):
     *   idToken = "mock_+919876543210"  →  phone = "+919876543210", skips Firebase entirely.
     *
     * Production:
     *   idToken = real Firebase ID token  →  verified with Firebase Admin SDK.
     */
    @PostMapping("/verify-firebase")
    @Transactional
    public ResponseEntity<ApiResponse<?>> verifyFirebase(
            @RequestBody @Valid AuthVerifyRequest request,
            HttpServletRequest httpRequest) {

        // Rate limit: 10 requests per minute per IP
        String clientIp = extractIp(httpRequest);
        if (!rateLimiter.tryConsume(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(ApiResponse.error("Too many requests. Try again in 1 minute.", "RATE_LIMITED"));
        }

        String idToken = request.idToken();

        // ── 1. Mock path — checked FIRST, no Firebase needed ─────────
        if (idToken.startsWith(MOCK_PREFIX)) {
            String phone       = idToken.substring(MOCK_PREFIX.length()); // e.g. "+919876543210"
            String firebaseUid = MOCK_PREFIX + phone;                     // e.g. "mock_+919876543210"

            AuthResponse response = userRepository.findByFirebaseUid(firebaseUid)
                    .map(existing -> new AuthResponse(
                            jwtService.generateToken(existing.getId()),
                            existing.getId(),
                            false,
                            existing.getDisplayName()))
                    .orElseGet(() -> {
                        User user = new User();
                        user.setFirebaseUid(firebaseUid);
                        user.setPhone(phone);
                        User saved = userRepository.save(user);
                        return new AuthResponse(
                                jwtService.generateToken(saved.getId()),
                                saved.getId(),
                                true,
                                null);
                    });

            return ResponseEntity.ok(ApiResponse.success(response));
        }

        // ── 2. Real Firebase path ─────────────────────────────────────
        AuthResponse authResponse = authService.verifyFirebaseToken(idToken);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    /** Extracts the real client IP, honouring X-Forwarded-For set by Railway's proxy. */
    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
