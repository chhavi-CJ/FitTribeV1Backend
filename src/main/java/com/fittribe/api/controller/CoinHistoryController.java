package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.repository.CoinTransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/coins")
public class CoinHistoryController {

    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final CoinTransactionRepository coinRepo;

    public CoinHistoryController(CoinTransactionRepository coinRepo) {
        this.coinRepo = coinRepo;
    }

    // ── GET /api/v1/coins/history ─────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();

        LocalDate today     = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate yesterday = today.minusDays(1);

        List<Map<String, Object>> result = coinRepo
                .findTop50ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(tx -> {
                    Map<String, Object> m = new LinkedHashMap<>();

                    // Amount: positive for CREDIT, negative for DEBIT
                    int signedAmount = "DEBIT".equals(tx.getDirection())
                            ? -Math.abs(tx.getAmount())
                            :  Math.abs(tx.getAmount());
                    m.put("amount", signedAmount);
                    m.put("type",   tx.getType());
                    m.put("label",  tx.getLabel());
                    m.put("createdAt", tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : null);
                    m.put("formattedDate", formatDate(tx, today, yesterday));
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private String formatDate(CoinTransaction tx, LocalDate today, LocalDate yesterday) {
        if (tx.getCreatedAt() == null) return "";
        LocalDate txDate = LocalDate.ofInstant(tx.getCreatedAt(), ZoneId.of("Asia/Kolkata"));
        if (txDate.equals(today))     return "Today";
        if (txDate.equals(yesterday)) return "Yesterday";
        return txDate.format(DAY_FORMATTER);
    }
}
