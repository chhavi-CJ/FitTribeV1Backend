package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users/me/coins")
public class CoinController {

    private final UserRepository            userRepo;
    private final CoinTransactionRepository coinRepo;

    public CoinController(UserRepository userRepo, CoinTransactionRepository coinRepo) {
        this.userRepo = userRepo;
        this.coinRepo = coinRepo;
    }

    // ── GET /users/me/coins ───────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<?>> coins(Authentication auth) {
        UUID userId = userId(auth);

        int balance = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"))
                .getCoins();

        List<Map<String, Object>> transactions = coinRepo
                .findTop20ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(tx -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",        tx.getId());
                    m.put("amount",    tx.getAmount());
                    m.put("direction", tx.getDirection());
                    m.put("label",     tx.getLabel());
                    m.put("createdAt", tx.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance",      balance);
        data.put("transactions", transactions);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }
}
