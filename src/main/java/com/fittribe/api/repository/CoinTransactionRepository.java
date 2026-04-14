package com.fittribe.api.repository;

import com.fittribe.api.entity.CoinTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for {@code coin_transactions} table (extended V44).
 *
 * <p>Append-only ledger of every coin change. Balance is always derived as
 * SUM(delta) for a user. The {@code balance_after} denormalized field is
 * computed during backfill (V44) and during each new award/revoke.
 *
 * <p>Supports both legacy coin awards (V7 schema: amount + direction) and
 * PR System V2 ledger entries (V44+ schema: delta + reason + reference_type).
 * Phase 2 code will populate the new columns; Phase 1 data foundation leaves
 * them available for future use.
 */
@Repository
public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, UUID> {

    /**
     * Legacy method: fetch top 20 most recent transactions for a user.
     * Used by existing {@code CoinController} and {@code CoinHistoryController}.
     * Retained for backward compatibility; Phase 2+ code should use paginated queries.
     */
    List<CoinTransaction> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Fetch the most recent transaction for a user (to derive current balance).
     * Used by {@code GET /user/balance}.
     */
    Optional<CoinTransaction> findTop1ByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Fetch paginated history for a user, newest first.
     * Used by {@code GET /coins/history} for the history tab.
     */
    List<CoinTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Legacy method from V24: idempotency check for old coin award code.
     * Returns true if this exact (type, reference_id) has already been awarded to this user.
     */
    boolean existsByUserIdAndTypeAndReferenceId(UUID userId, String type, String referenceId);
}
