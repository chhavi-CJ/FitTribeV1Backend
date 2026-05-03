package com.fittribe.api.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles referral position jumps in an independent transaction (REQUIRES_NEW)
 * so that a failure here never rolls back the caller's signup transaction.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    private final WaitlistRepository repo;

    public ReferralService(WaitlistRepository repo) {
        this.repo = repo;
    }

    /**
     * Bumps the referrer up the waitlist queue. Runs in its own transaction so
     * any DB error rolls back only this operation — the new user's INSERT is unaffected.
     *
     * Jump curve (per referral, subtracted from current position, floor at 50):
     *   1st → 15 spots
     *   2nd → 12 spots
     *   3rd → 10 spots
     *   4th →  8 spots
     *   5th →  7 spots
     *   6th+ →  3 spots
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyReferralJump(String referrerCode) {
        Optional<WaitlistEntry> opt = repo.findByReferralCode(referrerCode);
        if (opt.isEmpty()) return;

        WaitlistEntry referrer = opt.get();
        int newRefCount = referrer.getReferralCount() + 1;
        int jump = switch (newRefCount) {
            case 1 -> 15;
            case 2 -> 12;
            case 3 -> 10;
            case 4 -> 8;
            case 5 -> 7;
            default -> 3;
        };
        int oldPosition = referrer.getPosition();
        int newPosition = Math.max(50, oldPosition - jump);

        referrer.setReferralCount(newRefCount);

        if (newPosition < oldPosition) {
            // Shift everyone in [newPosition, oldPosition) down by 1 to vacate the slot.
            repo.shiftPositionsDown(newPosition, oldPosition);
            referrer.setPosition(newPosition);
        }

        repo.save(referrer);
        log.info("Referral jump applied: code={} {} -> {} (jump={})",
                referrerCode, oldPosition, newPosition, jump);
    }
}
