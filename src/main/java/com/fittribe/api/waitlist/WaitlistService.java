// WaitlistService.java
package com.fittribe.api.waitlist;

import com.fittribe.api.waitlist.dto.WaitlistResponse;
import com.fittribe.api.waitlist.dto.WaitlistSubmitRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class WaitlistService {

    private static final int REFERRAL_CODE_LENGTH = 6;
    private static final String REFERRAL_CODE_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // no confusing chars (0/O, 1/I, etc.)

    private final WaitlistRepository repo;
    private final SecureRandom random = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    private final WaitlistEmailService emailService;

    public WaitlistService(WaitlistRepository repo, WaitlistEmailService emailService) {
        this.repo = repo;
        this.emailService = emailService;
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
        emailService.sendConfirmation(saved.getEmail(), saved.getReferralCode(), saved.getPosition());

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
     * Referral jump curve — matches the copy on the landing page.
     * Each successful referral (by phone-verified install eventually; for now, by signup)
     * bumps the referrer UP the queue.
     *
     *   1st referral → jump ~70 spots
     *   2nd          → jump ~20 spots
     *   3rd          → jump ~10 spots
     *   4th          → jump ~10 spots
     *   5th+         → jump ~5 spots each
     */
    @Transactional
    protected void applyReferralJump(String referrerCode) {
        Optional<WaitlistEntry> opt = repo.findByReferralCode(referrerCode);
        if (opt.isEmpty()) return;

        WaitlistEntry referrer = opt.get();
        int newRefCount = referrer.getReferralCount() + 1;
        int jump = switch (newRefCount) {
            case 1 -> 70;
            case 2 -> 20;
            case 3, 4 -> 10;
            default -> 5;
        };
        int newPosition = Math.max(1, referrer.getPosition() - jump);

        referrer.setReferralCount(newRefCount);
        referrer.setPosition(newPosition);
        repo.save(referrer);
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