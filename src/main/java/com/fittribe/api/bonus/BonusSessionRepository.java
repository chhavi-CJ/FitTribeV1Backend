package com.fittribe.api.bonus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BonusSessionRepository
        extends JpaRepository<BonusSessionGenerated, BonusSessionGeneratedId> {

    /**
     * All bonus sessions generated for a user on a specific date.
     * Used for idempotency check and for determining the next bonus_number.
     */
    List<BonusSessionGenerated> findByIdUserIdAndIdDate(UUID userId, LocalDate date);

    /**
     * Count of bonuses generated for a user in a date range.
     * Used by the weekly soft cap logic and by the resolver to gauge fatigue.
     */
    int countByIdUserIdAndIdDateBetween(UUID userId, LocalDate from, LocalDate to);
}
