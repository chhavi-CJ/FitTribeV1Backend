package com.fittribe.api.fitnesssummary;

/**
 * Result summary returned by {@link FitnessSummaryService#runNightlyJob(java.time.Instant)}.
 *
 * @param processed total number of active users attempted
 * @param succeeded users whose summary was computed and upserted successfully
 * @param failed    users where computation threw an exception (logged separately)
 */
public record NightlyJobResult(int processed, int succeeded, int failed) {}
