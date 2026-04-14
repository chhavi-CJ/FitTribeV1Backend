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
     * Awards coins to a user with idempotency guarantee and deferred debt settlement.
     * If a transaction with the same (userId, type, referenceId) already exists,
     * this call is a no-op — safe to call multiple times.
     *
     * Implements deferred debt settlement per HLD §4: if user has pending debt
     * from revoked PRs, incoming coins first pay off debt, then add to balance.
     * balance_after is always ≥ 0. debt_after tracks how much is still owed.
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

        // Read current ledger state from most recent transaction
        int currentBalance = 0;
        int currentDebt = 0;
        var lastTx = coinRepo.findTop1ByUserIdOrderByCreatedAtDesc(userId);
        if (lastTx.isPresent()) {
            if (lastTx.get().getBalanceAfter() != null) {
                currentBalance = lastTx.get().getBalanceAfter();
            }
            if (lastTx.get().getDebtAfter() != null) {
                currentDebt = lastTx.get().getDebtAfter();
            }
        }

        // Build transaction with debt settlement logic
        CoinTransaction tx = new CoinTransaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setLabel(label);
        tx.setReferenceId(referenceId);
        tx.setDebtBefore(currentDebt);

        int balanceDelta;  // how much users.coins actually changes (signed)

        if (amount > 0) {
            // CREDIT: pay off debt first, remainder goes to balance
            int payOff = Math.min(amount, currentDebt);
            int toBalance = amount - payOff;
            int newBalance = currentBalance + toBalance;
            int newDebt = currentDebt - payOff;

            tx.setAmount(amount);
            tx.setDirection("CREDIT");
            tx.setDelta(amount);
            tx.setBalanceAfter(newBalance);
            tx.setDebtAfter(newDebt);
            tx.setClampedAmount(payOff);

            balanceDelta = toBalance;  // only what actually hit balance
        } else {
            // DEBIT: revoke from balance, rest accrues as debt
            int revokeAmount = Math.abs(amount);
            int actualDeducted = Math.min(revokeAmount, currentBalance);
            int clamped = revokeAmount - actualDeducted;
            int newBalance = currentBalance - actualDeducted;
            int newDebt = currentDebt + clamped;

            tx.setAmount(actualDeducted);
            tx.setDirection("DEBIT");
            tx.setDelta(-actualDeducted);
            tx.setBalanceAfter(newBalance);
            tx.setDebtAfter(newDebt);
            tx.setClampedAmount(clamped);

            balanceDelta = -actualDeducted;  // users.coins only decrements by what was actually taken
        }

        coinRepo.save(tx);

        // Update users.coins denormalized balance by balanceDelta, not amount
        // This ensures users.coins always matches the most recent balance_after
        jdbcTemplate.update(
            "UPDATE users SET coins = coins + ? WHERE id = ?",
            balanceDelta, userId);

        log.debug("Coins awarded: userId={} amount={} direction={} debt_before={} debt_after={} type={} ref={}",
                userId, amount, (amount > 0 ? "CREDIT" : "DEBIT"), currentDebt,
                (amount > 0 ? currentDebt - Math.min(amount, currentDebt) : currentDebt + Math.abs(amount) - Math.min(Math.abs(amount), currentBalance)),
                type, referenceId);
    }
}
