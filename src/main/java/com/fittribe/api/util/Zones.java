package com.fittribe.api.util;

import java.time.ZoneId;

/**
 * Application-wide timezone for "what day/week is it now" computations.
 * This is the timezone of our user base (India), NOT the server timezone.
 *
 * Use APP_ZONE for:
 * - LocalDate.now(APP_ZONE) — getting today's date
 * - localDate.atStartOfDay(APP_ZONE) — day boundary as Instant
 * - localDate.with(TemporalAdjusters.previousOrSame(MONDAY)) with APP_ZONE-derived dates
 *
 * Do NOT use APP_ZONE for:
 * - Storing Instants in the DB (always UTC)
 * - Comparing Instants (timezone-agnostic by definition)
 */
public final class Zones {
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");
    private Zones() {}
}
