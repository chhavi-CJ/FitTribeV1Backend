package com.fittribe.api.service;

import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.repository.CoinTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CoinService {

    private static final Logger log = LoggerFactory.getLogger(CoinService.class);

    private final CoinTransactionRepository coinRepo;
    private final JdbcTemplate              jdbcTemplate;

    public CoinService(CoinTransactionRepository coinRepo, JdbcTemplate jdbcTemplate) {
        this.coinRepo     = coinRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Awards coins to a user with idempotency guarantee.
     * If a transaction with the same (userId, type, referenceId) already exists,
     * this call is a no-op — safe to call multiple times.
     *
     * @param userId      recipient
     * @param amount      positive = CREDIT, negative = DEBIT
     * @param type        event type constant e.g. "LOG_WORKOUT", "PERSONAL_RECORD"
     * @param label       human-readable description shown in history
     * @param referenceId unique identifier for this specific event instance
     */
    @Transactional
    public void awardCoins(UUID userId, int amount, String type, String label, String referenceId) {
        // Idempotency: skip if this exact event has already been recorded
        if (coinRepo.existsByUserIdAndTypeAndReferenceId(userId, type, referenceId)) {
            log.debug("Coin award skipped (duplicate): userId={} type={} ref={}", userId, type, referenceId);
            return;
        }

        // Persist the transaction record
        CoinTransaction tx = new CoinTransaction();
        tx.setUserId(userId);
        tx.setAmount(Math.abs(amount));
        tx.setDirection(amount > 0 ? "CREDIT" : "DEBIT");
        tx.setLabel(label);
        tx.setType(type);
        tx.setReferenceId(referenceId);
        coinRepo.save(tx);

        // Atomic balance update — avoids read-modify-write race conditions
        jdbcTemplate.update("UPDATE users SET coins = coins + ? WHERE id = ?", amount, userId);

        log.debug("Coins awarded: userId={} amount={} type={} ref={}", userId, amount, type, referenceId);
    }
}
