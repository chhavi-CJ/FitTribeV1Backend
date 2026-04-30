package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.repository.DeviceTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceTokenRepository deviceTokenRepo;

    public DeviceController(DeviceTokenRepository deviceTokenRepo) {
        this.deviceTokenRepo = deviceTokenRepo;
    }

    public record RegisterRequest(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB",
                               message = "platform must be ANDROID, IOS, or WEB") String platform) {}

    public record UnregisterRequest(@NotBlank String token) {}

    // ── POST /devices/register ────────────────────────────────────────
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<ApiResponse<?>> register(
            @Valid @RequestBody RegisterRequest req,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        deviceTokenRepo.upsert(userId, req.token(), req.platform());
        return ResponseEntity.ok(ApiResponse.success(Map.of("registered", true)));
    }

    // ── DELETE /devices/unregister ────────────────────────────────────
    @DeleteMapping("/unregister")
    @Transactional
    public ResponseEntity<ApiResponse<?>> unregister(
            @Valid @RequestBody UnregisterRequest req,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        deviceTokenRepo.deleteByUserIdAndToken(userId, req.token());
        return ResponseEntity.ok(ApiResponse.success(Map.of("unregistered", true)));
    }
}
