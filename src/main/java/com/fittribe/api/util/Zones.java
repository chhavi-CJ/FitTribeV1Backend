package com.fittribe.api.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Application-wide timezone for "what day/week is it now" computations.
 * This is the timezone of our user base (India), NOT the server timezone.
 *
 * Use APP_ZONE for:
 * - LocalDate.now(APP_ZONE) — getting today's calendar date
 * - localDate.atStartOfDay(APP_ZONE) — midnight boundary as Instant
 * - localDate.with(TemporalAdjusters.previousOrSame(MONDAY)) with APP_ZONE-derived dates
 *
 * Use fitnessDayNow() / fitnessDayStart() for:
 * - Session/plan "what day is it for the user" logic (5am IST boundary)
 * - Before 05:00 IST the user is still in the previous fitness day
 *
 * Do NOT use APP_ZONE for:
 * - Storing Instants in the DB (always UTC)
 * - Comparing Instants (timezone-agnostic by definition)
 */
public final class Zones {
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Kolkata");

    private Zones() {}

    /**
     * Returns the fitness day that contains {@code when}.
     * A fitness day runs 05:00 IST → 04:59 IST the next calendar day,
     * so midnight–04:59 IST still belongs to the previous calendar date.
     */
    public static LocalDate fitnessDay(Instant when) {
        ZonedDateTime zdt = when.atZone(APP_ZONE);
        if (zdt.getHour() < 5) {
            return zdt.toLocalDate().minusDays(1);
        }
        return zdt.toLocalDate();
    }

    /** Fitness day for right now. */
    public static LocalDate fitnessDayNow() {
        return fitnessDay(Instant.now());
    }

    /**
     * Returns the Instant when {@code fitnessDay} begins (05:00 IST).
     * Pass the result of fitnessDayNow() or fitnessDay(someInstant).
     */
    public static Instant fitnessDayStart(LocalDate fitnessDay) {
        return fitnessDay.atTime(5, 0).atZone(APP_ZONE).toInstant();
    }
}
