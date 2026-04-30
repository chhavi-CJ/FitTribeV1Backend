package com.fittribe.api.scheduler;

import com.fittribe.api.entity.BonusFreezeGrant;
import com.fittribe.api.repository.BonusFreezeGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Daily 00:30 IST — marks expired bonus freeze grants as consumed.
 *
 * Logic:
 * 1. Query all unconsumed grants where expiresAt <= now.
 * 2. For each grant: set consumedAt = now, consumptionReason = "EXPIRED".
 * 3. Batch save all marked grants.
 * 4. Log the count.
 */
@Component
public class BonusFreezeExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BonusFreezeExpiryScheduler.class);

    private final BonusFreezeGrantRepository bonusFreezeGrantRepository;

    public BonusFreezeExpiryScheduler(BonusFreezeGrantRepository bonusFreezeGrantRepository) {
        this.bonusFreezeGrantRepository = bonusFreezeGrantRepository;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void markExpiredGrants() {
        Instant now = Instant.now();
        List<BonusFreezeGrant> expired = bonusFreezeGrantRepository.findExpiredUnconsumed(now);

        for (BonusFreezeGrant grant : expired) {
            grant.setConsumedAt(now);
            grant.setConsumptionReason("EXPIRED");
        }

        bonusFreezeGrantRepository.saveAll(expired);
        log.info("BonusFreezeExpiry: marked {} grant(s) as EXPIRED", expired.size());
    }
}
