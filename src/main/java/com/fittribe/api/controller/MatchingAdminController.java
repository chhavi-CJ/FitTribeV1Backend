package com.fittribe.api.controller;

import com.fittribe.api.matching.BatchSummary;
import com.fittribe.api.matching.MatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint to trigger a Conscious Matching batch run ad-hoc.
 *
 * <p>Auth: requires the {@code X-Admin-Key} header to match the
 * {@code matching.admin-key} property (sourced from the {@code ADMIN_KEY}
 * env var in Railway). If the property is empty (not configured) OR the
 * header doesn't match, returns 403. Deny-by-default — same pattern as
 * the existing ADMIN_SECRET-based admin endpoints in this codebase.
 *
 * <p>The cron ({@link com.fittribe.api.matching.MatchingScheduler}) fires
 * the same logic daily at 09:00 IST. This endpoint exists for ad-hoc
 * runs (smoke testing, demo prep, recovery after an incident).
 */
@RestController
@RequestMapping("/api/admin/matching")
public class MatchingAdminController {

    private static final Logger log = LoggerFactory.getLogger(MatchingAdminController.class);
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final MatchingService matchingService;
    private final String adminKey;

    @Autowired
    public MatchingAdminController(
            MatchingService matchingService,
            @Value("${matching.admin-key:}") String adminKey) {
        this.matchingService = matchingService;
        this.adminKey = adminKey;
    }

    @PostMapping("/run-batch")
    public ResponseEntity<?> runBatch(@RequestHeader(value = ADMIN_HEADER, required = false)
                                      String providedKey) {
        if (adminKey == null || adminKey.isBlank()) {
            log.warn("/api/admin/matching/run-batch called but matching.admin-key is not " +
                     "configured — rejecting (deny by default)");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (providedKey == null || !adminKey.equals(providedKey)) {
            log.warn("/api/admin/matching/run-batch rejected — bad or missing {} header",
                    ADMIN_HEADER);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("/api/admin/matching/run-batch authorized — triggering batch");
        BatchSummary summary = matchingService.runBatch();
        log.info("/api/admin/matching/run-batch complete — groupsFormed={}, usersMatched={}, " +
                 "usersRemaining={}, errors={}",
                summary.groupsFormed(), summary.usersMatched(),
                summary.usersRemaining(), summary.errors().size());
        return ResponseEntity.ok(summary);
    }
}
