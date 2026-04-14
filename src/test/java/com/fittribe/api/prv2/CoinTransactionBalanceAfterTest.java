package com.fittribe.api.prv2;

import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.CoinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CoinTransaction ledger invariants (V44).
 * Verifies that balance_after is correctly computed and persisted when coins are awarded.
 *
 * Uses @SpringBootTest with real database to test the full CoinService.awardCoins flow,
 * including the balance_after computation logic added in Phase 1.
 */
@Disabled("Requires Testcontainers setup (Phase 1b). Production code path is additive to existing CoinService. Re-enable after test infrastructure ships.")
@SpringBootTest
@Transactional
@DisplayName("CoinTransaction — Balance Ledger Invariant")
class CoinTransactionBalanceAfterTest {

    @Autowired
    private CoinService coinService;

    @Autowired
    private CoinTransactionRepository coinRepo;

    @Autowired
    private UserRepository userRepo;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        // Create a test user
        User user = new User();
        user.setPhone("+91" + System.currentTimeMillis());
        user.setDisplayName("Test User");
        user.setCoins(0);
        user = userRepo.saveAndFlush(user);
        testUserId = user.getId();
    }

    @Test
    @DisplayName("CoinService.awardCoins: First coin sets balance_after correctly")
    void testFirstCoinBalanceAfter() {
        // Award first coin: +10 CREDIT
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "Workout logged", "session_1");

        // Retrieve and verify balance_after
        List<CoinTransaction> txns = coinRepo.findTop20ByUserIdOrderByCreatedAtDesc(testUserId);
        assertEquals(1, txns.size());

        CoinTransaction tx = txns.get(0);
        assertEquals(testUserId, tx.getUserId());
        assertEquals(10, tx.getAmount());
        assertEquals("CREDIT", tx.getDirection());
        assertNotNull(tx.getBalanceAfter());
        assertEquals(10, tx.getBalanceAfter()); // Starting from 0, 0 + 10 = 10
    }

    @Test
    @DisplayName("CoinService.awardCoins: Second coin compounds balance correctly")
    void testSecondCoinBalanceAfterCompounds() {
        // First coin: +10
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "Workout 1", "session_1");

        // Second coin: +20 (different reference)
        coinService.awardCoins(testUserId, 20, "PERSONAL_RECORD", "PR achieved", "pr_1");

        // Retrieve both transactions, ordered newest first
        List<CoinTransaction> txns = coinRepo.findTop20ByUserIdOrderByCreatedAtDesc(testUserId);
        assertEquals(2, txns.size());

        // txns[0] is the most recent (second coin)
        CoinTransaction tx2 = txns.get(0);
        assertEquals(20, tx2.getAmount());
        assertEquals("CREDIT", tx2.getDirection());
        assertEquals(30, tx2.getBalanceAfter()); // 10 + 20 = 30

        // txns[1] is the first coin
        CoinTransaction tx1 = txns.get(1);
        assertEquals(10, tx1.getAmount());
        assertEquals(10, tx1.getBalanceAfter());
    }

    @Test
    @DisplayName("CoinService.awardCoins: DEBIT reduces balance correctly")
    void testDebitReducesBalance() {
        // First: credit 50
        coinService.awardCoins(testUserId, 50, "LOG_WORKOUT", "Workout", "session_1");

        // Second: debit 20 (negative amount)
        coinService.awardCoins(testUserId, -20, "PURCHASE", "Streak freeze", "purchase_1");

        List<CoinTransaction> txns = coinRepo.findTop20ByUserIdOrderByCreatedAtDesc(testUserId);
        assertEquals(2, txns.size());

        // Most recent is the debit
        CoinTransaction debit = txns.get(0);
        assertEquals(20, debit.getAmount());
        assertEquals("DEBIT", debit.getDirection());
        assertEquals(30, debit.getBalanceAfter()); // 50 - 20 = 30

        // First is the credit
        CoinTransaction credit = txns.get(1);
        assertEquals(50, credit.getAmount());
        assertEquals("CREDIT", credit.getDirection());
        assertEquals(50, credit.getBalanceAfter());
    }

    @Test
    @DisplayName("CoinService.awardCoins: Multiple transactions maintain monotonic balance_after")
    void testMultipleTransactionsMonotonicBalance() {
        // Sequence: +10, +20, -5, +15
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "W1", "w1");
        coinService.awardCoins(testUserId, 20, "LOG_WORKOUT", "W2", "w2");
        coinService.awardCoins(testUserId, -5, "PURCHASE", "P1", "p1");
        coinService.awardCoins(testUserId, 15, "BONUS", "B1", "b1");

        List<CoinTransaction> txns = coinRepo.findByUserIdOrderByCreatedAtDesc(testUserId, org.springframework.data.domain.PageRequest.of(0, 100));
        assertEquals(4, txns.size());

        // Verify balances in chronological order (reverse order from query)
        // txns[3] is oldest: +10 -> balance = 10
        assertEquals(10, txns.get(3).getBalanceAfter());

        // txns[2]: +20 -> balance = 30
        assertEquals(30, txns.get(2).getBalanceAfter());

        // txns[1]: -5 -> balance = 25
        assertEquals(25, txns.get(1).getBalanceAfter());

        // txns[0] is newest: +15 -> balance = 40
        assertEquals(40, txns.get(0).getBalanceAfter());
    }

    @Test
    @DisplayName("CoinService.awardCoins: Idempotency prevents duplicate entries")
    void testIdempotencyPreventsDoubleAward() {
        // Award same coin twice with same reference
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "Workout", "session_1");
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "Workout", "session_1");

        // Should only have one entry
        List<CoinTransaction> txns = coinRepo.findTop20ByUserIdOrderByCreatedAtDesc(testUserId);
        assertEquals(1, txns.size());
        assertEquals(10, txns.get(0).getBalanceAfter());
    }

    @Test
    @DisplayName("CoinTransaction: balance_after is NOT NULL (ledger invariant)")
    void testBalanceAfterNotNull() {
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "Workout", "session_1");

        List<CoinTransaction> txns = coinRepo.findTop20ByUserIdOrderByCreatedAtDesc(testUserId);
        assertEquals(1, txns.size());

        CoinTransaction tx = txns.get(0);
        assertNotNull(tx.getBalanceAfter(), "balance_after must never be null");
    }

    @Test
    @DisplayName("CoinTransaction: Phase 2 ledger fields default to appropriate values")
    void testPhase2LedgerFieldDefaults() {
        coinService.awardCoins(testUserId, 10, "LOG_WORKOUT", "Workout", "session_1");

        List<CoinTransaction> txns = coinRepo.findTop20ByUserIdOrderByCreatedAtDesc(testUserId);
        CoinTransaction tx = txns.get(0);

        // Phase 2 fields are set to defaults; Phase 2 code will populate them
        assertEquals(0, tx.getDebtBefore());
        assertEquals(0, tx.getDebtAfter());
        assertEquals(0, tx.getClampedAmount());
        // delta, reason, reference_type remain NULL until Phase 2
        assertNull(tx.getDelta());
        assertNull(tx.getReason());
        assertNull(tx.getReferenceType());
    }
}
