package com.fittribe.api.repository;

import com.fittribe.api.entity.FreezeTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FreezeTransactionRepository extends JpaRepository<FreezeTransaction, Long> {

    @Query("SELECT t FROM FreezeTransaction t WHERE t.userId = :userId " +
           "AND t.occurredAt >= :since ORDER BY t.occurredAt DESC")
    List<FreezeTransaction> findRecentForUser(
            @Param("userId") UUID userId,
            @Param("since")  Instant since,
            Pageable pageable);
}
