package com.fittribe.api.scheduler;

import com.fittribe.api.fitnesssummary.FitnessSummaryService;
import com.fittribe.api.fitnesssummary.NightlyJobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Nightly scheduler that refreshes the pre-computed fitness summary for
 * every active user.
 *
 * <p>Runs daily at 03:00 IST (Asia/Kolkata). Activity window: users who
 * completed at least one session in the last 60 days.
 *
 * <p>The outer try/catch here catches catastrophic failures (e.g. unable
 * to query the DB at all). Per-user failures are handled inside
 * {@link FitnessSummaryService#runNightlyJob(Instant)} and do not abort
 * the rest of the batch.
 */
@Component
public class FitnessSummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FitnessSummaryScheduler.class);

    private final FitnessSummaryService fitnessSummaryService;

    public FitnessSummaryScheduler(FitnessSummaryService fitnessSummaryService) {
        this.fitnessSummaryService = fitnessSummaryService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Kolkata")
    public void nightlyFitnessSummaryJob() {
        log.info("FitnessSummaryScheduler: starting nightly fitness summary job");
        try {
            NightlyJobResult result = fitnessSummaryService.runNightlyJob(Instant.now());
            log.info("FitnessSummaryScheduler: complete — processed={}, succeeded={}, failed={}",
                    result.processed(), result.succeeded(), result.failed());
        } catch (Exception e) {
            log.error("FitnessSummaryScheduler: job failed catastrophically", e);
        }
    }
}
