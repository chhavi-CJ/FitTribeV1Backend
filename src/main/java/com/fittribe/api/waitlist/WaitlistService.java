// WaitlistService.java
package com.fittribe.api.waitlist;

import com.fittribe.api.waitlist.dto.WaitlistResponse;
import com.fittribe.api.waitlist.dto.WaitlistSubmitRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);
    private static final int REFERRAL_CODE_LENGTH = 6;
    private static final String REFERRAL_CODE_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no confusing chars (0/O, 1/I, etc.)

    private final WaitlistRepository repo;
    private final SecureRandom random = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistService(WaitlistRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public WaitlistResponse submit(WaitlistSubmitRequest req) {
        // Dedupe: if email OR phone exists, return existing entry (idempotent)
        Optional<WaitlistEntry> existing = repo.findByEmailIgnoreCase(req.getEmail());
        if (existing.isEmpty()) {
            existing = repo.findByPhone(req.getPhone());
        }
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        // New entry
        WaitlistEntry entry = new WaitlistEntry();
        entry.setEmail(req.getEmail().trim());
        entry.setPhone(req.getPhone().trim());
        entry.setReferralCode(generateUniqueReferralCode());
        entry.setReferredByCode(req.getReferredByCode());

        WaitlistEntry saved = repo.save(entry);
        repo.flush();
        // Pull back the DB-assigned position and start_position (set via sequence + trigger).
        // flush forces the INSERT so DEFAULT-assigned position becomes readable via refresh()
        entityManager.refresh(saved);

        // Credit referrer (non-blocking if fails)
        if (req.getReferredByCode() != null && !req.getReferredByCode().isBlank()) {
            try {
                applyReferralJump(req.getReferredByCode());
            } catch (Exception e) {
                // Log and continue — referral credit failing shouldn't block signup
            }
        }

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public Optional<WaitlistResponse> findByCode(String referralCode) {
        return repo.findByReferralCode(referralCode).map(e -> toResponse(e, false));
    }

    @Transactional(readOnly = true)
    public long count() {
        return repo.count();
    }

    /**
     * Referral jump curve — matches the copy on the waitlist dashboard.
     * Each successful referral bumps the referrer UP the queue by this many spots:
     *
     *   1st referral → 15 spots
     *   2nd          → 12 spots
     *   3rd          → 10 spots
     *   4th          →  8 spots
     *   5th          →  7 spots
     *   6th+         →  3 spots each
     */
    @Transactional
    protected void applyReferralJump(String referrerCode) {
        try {
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
                // Shift everyone between newPosition and oldPosition down by 1
                // to vacate the target slot before moving the referrer into it.
                repo.shiftPositionsDown(newPosition, oldPosition);
                referrer.setPosition(newPosition);
            }

            repo.save(referrer);
        } catch (Exception e) {
            log.warn("Position recalculation failed for referrer {} — referral_count may not be saved: {}",
                    referrerCode, e.getMessage());
        }
    }

    private String generateUniqueReferralCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("W");
            for (int i = 0; i < REFERRAL_CODE_LENGTH - 1; i++) {
                sb.append(REFERRAL_CODE_ALPHABET.charAt(
                    random.nextInt(REFERRAL_CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (repo.findByReferralCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Could not generate unique referral code after 10 attempts");
    }

    private WaitlistResponse toResponse(WaitlistEntry e, boolean alreadyExists) {
        WaitlistResponse r = new WaitlistResponse();
        r.setReferralCode(e.getReferralCode());
        r.setPosition(e.getPosition());
        r.setStartPosition(e.getStartPosition());
        r.setReferralCount(e.getReferralCount());
        r.setAlreadyExists(alreadyExists);
        return r;
    }
}