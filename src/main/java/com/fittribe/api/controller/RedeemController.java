package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.service.CoinService;
import com.fittribe.api.service.FreezeTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coins")
public class RedeemController {

    private static final Logger  log                = LoggerFactory.getLogger(RedeemController.class);
    private static final int     STREAK_FREEZE_COST = 80;
    private static final int     GROUP_CROWN_COST   = 30;
    private static final ZoneId  IST                = ZoneId.of("Asia/Kolkata");

    private final JdbcTemplate              jdbcTemplate;
    private final CoinService               coinService;
    private final CoinTransactionRepository coinRepo;
    private final FreezeTransactionService  freezeTransactionService;

    public RedeemController(JdbcTemplate jdbcTemplate,
                            CoinService coinService,
                            CoinTransactionRepository coinRepo,
                            FreezeTransactionService freezeTransactionService) {
        this.jdbcTemplate            = jdbcTemplate;
        this.coinService             = coinService;
        this.coinRepo                = coinRepo;
        this.freezeTransactionService = freezeTransactionService;
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
        String refId = "STREAK_FREEZE:" + LocalDate.now(IST);

        // Idempotency guard: reject duplicate redeem on same day (don't silently no-op)
        if (coinRepo.existsByUserIdAndTypeAndReferenceId(userId, "STREAK_FREEZE_REDEEM", refId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REDEEMED",
                    "Already redeemed today");
        }

        if (currentCoins(userId) < STREAK_FREEZE_COST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_COINS",
                    "Insufficient coins");
        }

        // Debit through CoinService: handles balance_after, debt_after, delta,
        // clamped_amount, debt_before, and the users.coins denormalized update.
        coinService.awardCoins(userId, -STREAK_FREEZE_COST,
                "STREAK_FREEZE_REDEEM", "Extra streak freeze", refId);

        // Bank the freeze day
        jdbcTemplate.update(
                "UPDATE users SET purchased_freeze_balance = purchased_freeze_balance + 1 WHERE id = ?",
                userId);

        try {
            freezeTransactionService.record(userId, "PURCHASED", 1,
                    Map.of("coinsCost", 80));
        } catch (Exception e) {
            log.warn("Failed to record freeze tx for user={}", userId, e);
        }

        int newBalance    = currentCoins(userId);
        int freezeBalance = jdbcTemplate.queryForObject(
                "SELECT purchased_freeze_balance FROM users WHERE id = ?", Integer.class, userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newBalance",          newBalance);
        result.put("purchasedFreezeBalance", freezeBalance);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Group Crown ───────────────────────────────────────────────────
    private ResponseEntity<ApiResponse<?>> redeemGroupCrown(UUID userId) {
        // Guard: user must be in a group before they can redeem a crown.
        // (Fixes the known GROUP_CROWN-deducts-with-no-membership bug.)
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE user_id = ?",
                Integer.class, userId);
        if (memberCount == null || memberCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_GROUP_MEMBERSHIP",
                    "Join a group before redeeming a crown");
        }

        String refId = "GROUP_CROWN:" + LocalDate.now(IST);

        if (coinRepo.existsByUserIdAndTypeAndReferenceId(userId, "GROUP_CROWN_REDEEM", refId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REDEEMED",
                    "Already redeemed today");
        }

        if (currentCoins(userId) < GROUP_CROWN_COST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_COINS",
                    "Insufficient coins");
        }

        coinService.awardCoins(userId, -GROUP_CROWN_COST,
                "GROUP_CROWN_REDEEM", "Group crown 7 days", refId);

        jdbcTemplate.update(
                "UPDATE group_members SET crown_expires_at = NOW() + INTERVAL '7 days' WHERE user_id = ?",
                userId);

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