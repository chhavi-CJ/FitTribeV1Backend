package com.fittribe.api.controller;

import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.CoinService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/test")
public class TestAdminController {

    private final CoinService    coinService;
    private final UserRepository userRepo;

    public TestAdminController(CoinService coinService, UserRepository userRepo) {
        this.coinService = coinService;
        this.userRepo    = userRepo;
    }

    @GetMapping("/trigger-coins")
    public ResponseEntity<?> triggerCoins(
            @RequestParam("userId")            String userId,
            @RequestHeader("X-Test-Secret")    String secret) {

        if (!"fittribe-test-2024".equals(secret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        UUID uid = UUID.fromString(userId);

        coinService.awardCoins(uid, 10, "LOG_WORKOUT",      "Test workout",   "test-trigger-001");
        coinService.awardCoins(uid, 25, "PERSONAL_RECORD",  "Bench Press PR", "test-pr-001");

        int newBalance = userRepo.findById(uid)
                .map(u -> u.getCoins() != null ? u.getCoins() : 0)
                .orElse(-1);

        return ResponseEntity.ok(Map.of("newBalance", newBalance));
    }
}
