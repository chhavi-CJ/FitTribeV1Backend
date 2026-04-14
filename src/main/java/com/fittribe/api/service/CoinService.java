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

        // Compute balance_after for ledger invariant (V44).
        // Read the user's most recent transaction to establish starting balance.
        int startingBalance = 0;
        var lastTx = coinRepo.findTop1ByUserIdOrderByCreatedAtDesc(userId);
        if (lastTx.isPresent() && lastTx.get().getBalanceAfter() != null) {
            startingBalance = lastTx.get().getBalanceAfter();
        }

        // Compute new balance: direction determines sign
        String direction = amount > 0 ? "CREDIT" : "DEBIT";
        int delta = amount > 0 ? amount : -amount;
        int newBalance = startingBalance + (direction.equals("CREDIT") ? delta : -delta);

        // Persist the transaction record
        CoinTransaction tx = new CoinTransaction();
        tx.setUserId(userId);
        tx.setAmount(Math.abs(amount));
        tx.setDirection(direction);
        tx.setLabel(label);
        tx.setType(type);
        tx.setReferenceId(referenceId);
        tx.setBalanceAfter(newBalance);
        // debt_before, debt_after, clamped_amount left at defaults (0) — Phase 5 will populate
        coinRepo.save(tx);

        // Atomic balance update — avoids read-modify-write race conditions
        jdbcTemplate.update("UPDATE users SET coins = coins + ? WHERE id = ?", amount, userId);

        log.debug("Coins awarded: userId={} amount={} type={} ref={}", userId, amount, type, referenceId);
    }
}
