package com.fittribe.api.service;

import com.fittribe.api.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Computes 60-day session consistency stats for a user.
 * Used for the "Grinder" leaderboard dimension — rewards sustained gym presence
 * over a rolling window rather than a single week's performance.
 */
@Service
public class GrinderCalculator {

    public static final class GrinderResult {
        public final UUID       userId;
        public final int        totalSessions60Days;
        public final BigDecimal totalVolume60Days;

        public GrinderResult(UUID userId, int totalSessions60Days, BigDecimal totalVolume60Days) {
            this.userId              = userId;
            this.totalSessions60Days = totalSessions60Days;
            this.totalVolume60Days   = totalVolume60Days;
        }
    }

    private final WorkoutSessionRepository sessionRepo;

    public GrinderCalculator(WorkoutSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public GrinderResult compute(UUID userId) {
        Instant now          = Instant.now();
        Instant sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);

        int sessions = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", sixtyDaysAgo, now);
        BigDecimal volume = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(
                userId, sixtyDaysAgo, now);

        return new GrinderResult(userId, sessions,
                volume != null ? volume : BigDecimal.ZERO);
    }
}
