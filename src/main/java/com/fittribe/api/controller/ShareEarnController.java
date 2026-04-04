package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.service.CoinService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coins/earn")
public class ShareEarnController {

    private static final Set<String> VALID_PLATFORMS = Set.of("WHATSAPP", "INSTAGRAM", "TIKTOK");
    private static final int         SHARE_COINS     = 5;

    private final CoinService  coinService;
    private final JdbcTemplate jdbcTemplate;

    public ShareEarnController(CoinService coinService, JdbcTemplate jdbcTemplate) {
        this.coinService  = coinService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── POST /api/v1/coins/earn/share ─────────────────────────────────
    @PostMapping("/share")
    public ResponseEntity<ApiResponse<Map<String, Object>>> share(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        UUID   userId    = (UUID) auth.getPrincipal();
        String sessionId = body.get("sessionId");
        String platform  = body.get("platform");

        if (platform == null || !VALID_PLATFORMS.contains(platform)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLATFORM",
                    "platform must be one of: WHATSAPP, INSTAGRAM, TIKTOK");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_SESSION_ID",
                    "sessionId is required");
        }

        String referenceId = sessionId + "_" + platform;

        // Idempotency check: already awarded for this session + platform combination
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions " +
                "WHERE user_id = ? AND type = 'SHARE' AND reference_id = ?",
                Integer.class, userId, referenceId);

        if (count != null && count > 0) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("awarded", false);
            result.put("amount",  0);
            return ResponseEntity.ok(ApiResponse.success(result));
        }

        // Award coins — label = platform, referenceId = sessionId_platform
        coinService.awardCoins(userId, SHARE_COINS, "SHARE", platform, referenceId);

        int newBalance = toInt(jdbcTemplate.queryForObject(
                "SELECT coins FROM users WHERE id = ?", Integer.class, userId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("awarded",    true);
        result.put("amount",     SHARE_COINS);
        result.put("newBalance", newBalance);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private int toInt(Integer val) {
        return val != null ? val : 0;
    }
}
