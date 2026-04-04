package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coins")
public class RedeemController {

    private static final int STREAK_FREEZE_COST = 80;
    private static final int GROUP_CROWN_COST   = 30;

    private final UserRepository userRepo;
    private final JdbcTemplate   jdbcTemplate;

    public RedeemController(UserRepository userRepo, JdbcTemplate jdbcTemplate) {
        this.userRepo     = userRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── POST /api/v1/coins/redeem ─────────────────────────────────────
    @PostMapping("/redeem")
    @Transactional
    public ResponseEntity<ApiResponse<?>> redeem(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        String type = body.get("type");

        if ("STREAK_FREEZE".equals(type)) {
            return redeemStreakFreeze(userId);
        } else if ("GROUP_CROWN".equals(type)) {
            return redeemGroupCrown(userId);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TYPE",
                    "type must be STREAK_FREEZE or GROUP_CROWN");
        }
    }

    // ── Streak Freeze ─────────────────────────────────────────────────
    private ResponseEntity<ApiResponse<?>> redeemStreakFreeze(UUID userId) {
        int coins = currentCoins(userId);
        if (coins < STREAK_FREEZE_COST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_COINS",
                    "Insufficient coins");
        }

        jdbcTemplate.update(
                "UPDATE users SET coins = coins - ?, streak_freeze_balance = streak_freeze_balance + 1 WHERE id = ?",
                STREAK_FREEZE_COST, userId);

        jdbcTemplate.update(
                "INSERT INTO coin_transactions (id, user_id, amount, direction, label, type, reference_id, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, 'DEBIT', 'Extra streak freeze', 'STREAK_FREEZE_REDEEM', NULL, NOW())",
                userId, STREAK_FREEZE_COST);

        int newBalance         = currentCoins(userId);
        int freezeBalance      = jdbcTemplate.queryForObject(
                "SELECT streak_freeze_balance FROM users WHERE id = ?", Integer.class, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newBalance",          newBalance);
        result.put("streakFreezeBalance", freezeBalance);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Group Crown ───────────────────────────────────────────────────
    private ResponseEntity<ApiResponse<?>> redeemGroupCrown(UUID userId) {
        int coins = currentCoins(userId);
        if (coins < GROUP_CROWN_COST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_COINS",
                    "Insufficient coins");
        }

        jdbcTemplate.update(
                "UPDATE users SET coins = coins - ? WHERE id = ?",
                GROUP_CROWN_COST, userId);

        jdbcTemplate.update(
                "UPDATE group_members SET crown_expires_at = NOW() + INTERVAL '7 days' WHERE user_id = ?",
                userId);

        jdbcTemplate.update(
                "INSERT INTO coin_transactions (id, user_id, amount, direction, label, type, reference_id, created_at) " +
                "VALUES (gen_random_uuid(), ?, ?, 'DEBIT', 'Group crown 7 days', 'GROUP_CROWN_REDEEM', NULL, NOW())",
                userId, GROUP_CROWN_COST);

        int newBalance      = currentCoins(userId);
        Instant crownExpiry = jdbcTemplate.queryForObject(
                "SELECT MAX(crown_expires_at) FROM group_members WHERE user_id = ?",
                Instant.class, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newBalance",     newBalance);
        result.put("crownExpiresAt", crownExpiry);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Helper ────────────────────────────────────────────────────────
    private int currentCoins(UUID userId) {
        Integer coins = jdbcTemplate.queryForObject(
                "SELECT coins FROM users WHERE id = ?", Integer.class, userId);
        return coins != null ? coins : 0;
    }
}
