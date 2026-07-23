package com.fittribe.api.repository;

import com.fittribe.api.entity.BonusFreezeGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BonusFreezeGrantRepository extends JpaRepository<BonusFreezeGrant, Long> {

    @Query("SELECT g FROM BonusFreezeGrant g WHERE g.userId = :userId " +
           "AND g.consumedAt IS NULL AND g.validFrom <= :now AND g.expiresAt > :now " +
           "ORDER BY g.expiresAt ASC")
    List<BonusFreezeGrant> findActiveGrants(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT COUNT(g) FROM BonusFreezeGrant g WHERE g.userId = :userId " +
           "AND g.consumedAt IS NULL AND g.validFrom <= :now AND g.expiresAt > :now")
    long countActive(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT g FROM BonusFreezeGrant g WHERE g.consumedAt IS NULL AND g.expiresAt <= :now")
    List<BonusFreezeGrant> findExpiredUnconsumed(@Param("now") Instant now);

    @Query("SELECT COUNT(g) FROM BonusFreezeGrant g WHERE g.userId = :userId " +
           "AND g.earnedAt >= :weekStart AND g.earnedAt < :weekEnd")
    long countGrantsInWeek(@Param("userId") UUID userId,
                           @Param("weekStart") Instant weekStart,
                           @Param("weekEnd") Instant weekEnd);
}
