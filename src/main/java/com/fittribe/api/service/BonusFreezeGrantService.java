package com.fittribe.api.service;

import com.fittribe.api.entity.BonusFreezeGrant;
import com.fittribe.api.repository.BonusFreezeGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BonusFreezeGrantService {

    private static final Logger  log = LoggerFactory.getLogger(BonusFreezeGrantService.class);
    private static final ZoneId  IST = ZoneId.of("Asia/Kolkata");

    private final BonusFreezeGrantRepository repo;
    private final FreezeTransactionService   freezeTransactionService;

    public BonusFreezeGrantService(BonusFreezeGrantRepository repo,
                                   FreezeTransactionService freezeTransactionService) {
        this.repo                    = repo;
        this.freezeTransactionService = freezeTransactionService;
    }

    /**
     * Awards bonus freeze token(s) when the user hits their weekly workout goal.
     *
     * @param userId      the user who hit the goal
     * @param weeklyGoal  the user's configured weekly workout goal
     * @param weekMonday  the Monday that starts the current ISO week (in IST)
     * @return number of grants inserted (0 if not eligible or already granted this week)
     */
    public int grantIfEligible(UUID userId, int weeklyGoal, LocalDate weekMonday) {
        // 1. Idempotency: already granted this ISO week?
        Instant weekStart = weekMonday.atStartOfDay(IST).toInstant();
        Instant weekEnd   = weekMonday.plusDays(7).atStartOfDay(IST).toInstant();
        if (repo.countGrantsInWeek(userId, weekStart, weekEnd) > 0) {
            return 0;
        }

        // 2. How many freezes to grant?
        int amount;
        if (weeklyGoal == 2 || weeklyGoal == 3) {
            amount = 1;
        } else if (weeklyGoal >= 4 && weeklyGoal <= 6) {
            amount = 2;
        } else {
            return 0; // weeklyGoal == 1 or invalid — no bonus freeze
        }

        // 3. Timestamps:
        //    earnedAt  = now
        //    validFrom = next Monday 00:00 IST = weekMonday.plusDays(7)
        //    expiresAt = validFrom + 28 days
        Instant now       = Instant.now();
        Instant validFrom = weekMonday.plusDays(7).atStartOfDay(IST).toInstant();
        Instant expiresAt = validFrom.plus(28, ChronoUnit.DAYS);

        // 4. Insert `amount` grant rows
        for (int i = 0; i < amount; i++) {
            BonusFreezeGrant grant = new BonusFreezeGrant();
            grant.setUserId(userId);
            grant.setEarnedAt(now);
            grant.setValidFrom(validFrom);
            grant.setExpiresAt(expiresAt);
            // consumedAt and consumptionReason left null (unconsumed)
            repo.save(grant);
        }

        try {
            freezeTransactionService.record(userId, "BONUS_EARNED", amount,
                    Map.of("validFrom", validFrom.toString(),
                           "expiresAt", expiresAt.toString(),
                           "weekStart", weekStart.toString()));
        } catch (Exception e) {
            log.warn("Failed to record freeze tx for user={}", userId, e);
        }

        return amount;
    }

    public long countActive(UUID userId) {
        return repo.countActive(userId, Instant.now());
    }

    public List<BonusFreezeGrant> getActiveGrants(UUID userId) {
        return repo.findActiveGrants(userId, Instant.now());
    }

    /**
     * Marks a grant as consumed with the given reason.
     * Used by the Sunday cron (Task 5) to apply or expire bonus freezes.
     */
    public void consumeGrant(BonusFreezeGrant grant, String reason) {
        grant.setConsumedAt(Instant.now());
        grant.setConsumptionReason(reason);
        repo.save(grant);
    }
}
