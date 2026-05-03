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
    private final ReferralService    referralService;
    private final SecureRandom random = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    public WaitlistService(WaitlistRepository repo, ReferralService referralService) {
        this.repo            = repo;
        this.referralService = referralService;
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

        // Credit referrer — runs in its own transaction (REQUIRES_NEW) so a failure
        // here never rolls back the new user's INSERT above.
        if (req.getReferredByCode() != null && !req.getReferredByCode().isBlank()) {
            try {
                referralService.applyReferralJump(req.getReferredByCode());
            } catch (Exception e) {
                log.error("Referral jump failed for code={} — signup unaffected: {}",
                        req.getReferredByCode(), e.getMessage(), e);
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