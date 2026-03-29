package com.fittribe.api.repository;

import com.fittribe.api.entity.CoinTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoinTransactionRepository extends JpaRepository<CoinTransaction, UUID> {

    List<CoinTransaction> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);
}
