package com.fittribe.api.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily Conscious Matching batch run. Fires at 09:00 IST every day —
 * mornings, so users discover their matched group when they open the
 * app to start their day.
 *
 * <p>This is one line of glue: delegate to {@link MatchingService}.
 * All logic, error handling, and observability live in the service.
 * Failures inside runBatch are surfaced via the returned
 * {@link BatchSummary#errors()} (not exceptions) — the scheduler
 * itself does not need a try/catch.
 *
 * <p>Locked schedule: daily at 09:00 IST. To change cadence, edit
 * the cron expression. The admin endpoint
 * ({@code POST /api/admin/matching/run-batch}) provides ad-hoc
 * triggering outside the cron.
 */
@Component
public class MatchingScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchingScheduler.class);

    private final MatchingService matchingService;

    @Autowired
    public MatchingScheduler(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /** Daily at 09:00 IST. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void runDailyBatch() {
        log.info("MatchingScheduler firing — daily 09:00 IST Conscious Matching batch");
        BatchSummary summary = matchingService.runBatch();
        log.info("MatchingScheduler complete — groupsFormed={}, usersMatched={}, " +
                 "usersRemaining={}, errors={}",
                summary.groupsFormed(), summary.usersMatched(),
                summary.usersRemaining(), summary.errors().size());
    }
}
