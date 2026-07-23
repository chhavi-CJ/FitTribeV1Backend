package com.fittribe.api.fitnesssummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.UserFitnessSummary;
import com.fittribe.api.repository.SessionFeedbackRepository;
import com.fittribe.api.repository.UserFitnessSummaryRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.util.MuscleGroupUtil;
import static com.fittribe.api.util.Zones.APP_ZONE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes and persists pre-aggregated training history snapshots.
 *
 * <p>Core method is {@link #computeSummary(UUID, Instant)}, which reads from
 * {@code set_logs}, {@code workout_sessions}, {@code user_session_feedback},
 * and {@code pr_events}, then returns a {@link FitnessSummary} DTO.
 *
 * <p>{@link #runNightlyJob(Instant)} iterates all users active in the last
 * 60 days, computes, and upserts — wrapping each user in a try/catch so
 * one failure never aborts the batch.
 *
 * <p>This service is read-only from the perspective of the domain:
 * it never writes to {@code set_logs}, {@code workout_sessions},
 * or {@code pr_events}. It only writes to {@code user_fitness_summary}.
 */
@Service
public class FitnessSummaryService {

    private static final Logger log = LoggerFactory.getLogger(FitnessSummaryService.class);

    // Rating order easy → hard; index = ordinal for comparison
    private static final List<String> RATING_ORDER =
            List.of("TOO_EASY", "GOOD", "HARD", "KILLED_ME");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate                  jdbc;
    private final UserFitnessSummaryRepository  summaryRepo;
    private final WorkoutSessionRepository      sessionRepo;
    private final SessionFeedbackRepository     feedbackRepo;
    private final ObjectMapper                  mapper;

    public FitnessSummaryService(JdbcTemplate jdbc,
                                 UserFitnessSummaryRepository summaryRepo,
                                 WorkoutSessionRepository sessionRepo,
                                 SessionFeedbackRepository feedbackRepo,
                                 ObjectMapper mapper) {
        this.jdbc        = jdbc;
        this.summaryRepo = summaryRepo;
        this.sessionRepo = sessionRepo;
        this.feedbackRepo = feedbackRepo;
        this.mapper      = mapper;
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Compute a fresh {@link FitnessSummary} for the given user.
     *
     * <p>No DB writes here — pure computation. Call {@link #upsertSummary}
     * to persist the result.
     *
     * @param userId the user to compute for
     * @param asOf   reference point for all time windows (use {@code Instant.now()} in production)
     */
    public FitnessSummary computeSummary(UUID userId, Instant asOf) {
        Instant win2wStart  = asOf.minus(14, ChronoUnit.DAYS);
        Instant win4wStart  = asOf.minus(28, ChronoUnit.DAYS);
        Instant win8wStart  = asOf.minus(56, ChronoUnit.DAYS);

        List<FitnessSummary.MainLiftEntry>      mainLifts   = computeMainLiftStrength(userId, win4wStart, asOf);
        Map<String, FitnessSummary.MuscleVolume> muscleVol  = computeMuscleVolume(userId, win2wStart, asOf);
        FitnessSummary.WeeklyConsistency        consistency = computeWeeklyConsistency(userId, win2wStart, asOf);
        FitnessSummary.RpeTrend                 rpeTrend    = computeRpeTrend(userId, win4wStart, asOf, win8wStart);
        FitnessSummary.PrActivity               prActivity  = computePrActivity(userId, win4wStart, asOf);
        Map<String, Integer>                    lastTrained = computeLastTrainedByMuscle(userId, asOf);

        return new FitnessSummary(1, mainLifts, muscleVol, consistency, rpeTrend, prActivity, lastTrained);
    }

    /**
     * Persist (insert or update) a computed summary for the given user.
     *
     * @param userId       target user
     * @param summary      computed summary DTO
     * @param sampleWindow human-readable window label, e.g. "2026-03-27 to 2026-04-22"
     */
    public void upsertSummary(UUID userId, FitnessSummary summary, String sampleWindow) {
        try {
            String json = mapper.writeValueAsString(summary);
            summaryRepo.upsert(userId, json, sampleWindow);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert fitness summary for userId=" + userId, e);
        }
    }

    /**
     * Read the stored summary for a user.
     *
     * @return empty if no summary has been computed yet (new user or first-run)
     */
    public Optional<FitnessSummary> getSummary(UUID userId) {
        return summaryRepo.findById(userId).flatMap(entity -> {
            try {
                return Optional.of(mapper.readValue(entity.getSummary(), FitnessSummary.class));
            } catch (Exception e) {
                log.warn("Failed to deserialise fitness summary for userId={}: {}", userId, e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * Nightly batch: compute and upsert summaries for every user who has
     * completed at least one session in the last 60 days.
     *
     * <p>Each user is wrapped in a try/catch so one failure never aborts
     * the rest of the batch.
     *
     * @param asOf reference time (injected so the scheduler can pass {@code Instant.now()})
     * @return result counts for the caller to log
     */
    public NightlyJobResult runNightlyJob(Instant asOf) {
        List<UUID> activeUsers = findActiveUserIds(asOf);
        log.info("FitnessSummaryService.runNightlyJob: {} active users to process", activeUsers.size());

        // Build sample window label: earliest window start to asOf date
        String sampleWindow = buildSampleWindow(asOf);

        int succeeded = 0;
        int failed    = 0;

        for (UUID userId : activeUsers) {
            try {
                FitnessSummary summary = computeSummary(userId, asOf);
                upsertSummary(userId, summary, sampleWindow);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("FitnessSummaryService: failed to process userId={}", userId, e);
            }
        }

        log.info("FitnessSummaryService.runNightlyJob complete: processed={}, succeeded={}, failed={}",
                activeUsers.size(), succeeded, failed);

        return new NightlyJobResult(activeUsers.size(), succeeded, failed);
    }

    // ── Signal computation ────────────────────────────────────────────

    /**
     * Heaviest set per exercise in the last 4 weeks.
     * Returns one entry per exercise, ordered by weight descending.
     */
    private List<FitnessSummary.MainLiftEntry> computeMainLiftStrength(UUID userId,
                                                                         Instant from,
                                                                         Instant to) {
        // DISTINCT ON (exercise_id): heaviest set, tie-break by reps DESC
        List<Object[]> rows = jdbc.query("""
                SELECT DISTINCT ON (sl.exercise_id)
                       sl.exercise_id,
                       sl.weight_kg,
                       sl.reps,
                       ws.finished_at,
                       e.muscle_group
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.session_id = ws.id
                JOIN exercises e         ON sl.exercise_id = e.id
                WHERE ws.user_id    = ?
                  AND ws.status     = 'COMPLETED'
                  AND ws.finished_at >= ?
                  AND ws.finished_at <  ?
                  AND sl.weight_kg IS NOT NULL
                ORDER BY sl.exercise_id, sl.weight_kg DESC, sl.reps DESC
                """,
                (rs, row) -> new Object[]{
                        rs.getString("exercise_id"),
                        rs.getBigDecimal("weight_kg"),
                        rs.getInt("reps"),
                        rs.getTimestamp("finished_at"),
                        rs.getString("muscle_group")
                },
                userId, from, to);

        List<FitnessSummary.MainLiftEntry> entries = new ArrayList<>();
        for (Object[] row : rows) {
            String exerciseId  = (String) row[0];
            BigDecimal weightKg = (BigDecimal) row[1];
            int reps           = (int) row[2];
            java.sql.Timestamp ts = (java.sql.Timestamp) row[3];
            String rawMuscle   = (String) row[4];

            String liftedDate  = ts != null
                    ? LocalDate.ofInstant(ts.toInstant(), ZoneOffset.UTC).format(DATE_FMT)
                    : null;
            String muscleGroup = MuscleGroupUtil.canonicalize(rawMuscle);
            if (muscleGroup.isEmpty()) muscleGroup = null;

            entries.add(new FitnessSummary.MainLiftEntry(
                    exerciseId,
                    weightKg != null ? weightKg.doubleValue() : null,
                    reps,
                    liftedDate,
                    muscleGroup));
        }
        return entries;
    }

    /**
     * Total working sets per canonical muscle group over the last 2 weeks.
     * Rows with unmapped muscle groups are silently dropped.
     */
    private Map<String, FitnessSummary.MuscleVolume> computeMuscleVolume(UUID userId,
                                                                           Instant from,
                                                                           Instant to) {
        List<Object[]> rows = jdbc.query("""
                SELECT e.muscle_group, COUNT(*) AS set_count
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.session_id = ws.id
                JOIN exercises e         ON sl.exercise_id = e.id
                WHERE ws.user_id    = ?
                  AND ws.status     = 'COMPLETED'
                  AND ws.finished_at >= ?
                  AND ws.finished_at <  ?
                GROUP BY e.muscle_group
                """,
                (rs, row) -> new Object[]{ rs.getString("muscle_group"), rs.getInt("set_count") },
                userId, from, to);

        // Accumulate into canonical groups (multiple DB values → same canonical group)
        Map<String, Integer> rawCounts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String canonical = MuscleGroupUtil.canonicalize((String) row[0]);
            if (canonical.isEmpty()) continue;
            rawCounts.merge(canonical, (int) row[1], Integer::sum);
        }

        Map<String, FitnessSummary.MuscleVolume> result = new LinkedHashMap<>();
        rawCounts.forEach((muscle, sets) -> result.put(muscle, FitnessSummary.MuscleVolume.of(sets)));
        return result;
    }

    /**
     * Session frequency signal over the last 2 weeks.
     */
    private FitnessSummary.WeeklyConsistency computeWeeklyConsistency(UUID userId,
                                                                        Instant from,
                                                                        Instant to) {
        // Total sessions in the 2-week window
        int total2w = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(userId, "COMPLETED", from, to);
        double avg  = Math.round((total2w / 2.0) * 10.0) / 10.0;  // round to 1 dp

        // Current ISO week: Monday 00:00 IST to now
        LocalDate monday = LocalDate.now(APP_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart = monday.atStartOfDay(APP_ZONE).toInstant();
        int currentWeekSessions = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekStart, to);

        // Weekly goal from users table
        Integer weeklyGoal = jdbc.queryForObject(
                "SELECT COALESCE(weekly_goal, 4) FROM users WHERE id = ?",
                Integer.class, userId);
        int goal = weeklyGoal != null ? weeklyGoal : 4;

        return new FitnessSummary.WeeklyConsistency(
                avg, currentWeekSessions, goal,
                FitnessSummary.WeeklyConsistency.labelFor(avg, goal));
    }

    /**
     * Categorical effort trend derived from session feedback ratings.
     *
     * <p>Each 4-week window uses the MODAL rating (most common). Tie-break:
     * harder rating wins. "unknown" if either window has fewer than 2 entries.
     */
    private FitnessSummary.RpeTrend computeRpeTrend(UUID userId,
                                                     Instant currentFrom,
                                                     Instant currentTo,
                                                     Instant previousFrom) {
        List<SessionFeedback> current  = feedbackRepo.findByUserIdAndCreatedAtBetween(userId, currentFrom, currentTo);
        List<SessionFeedback> previous = feedbackRepo.findByUserIdAndCreatedAtBetween(userId, previousFrom, currentFrom);

        int currentSize  = current.size();
        int previousSize = previous.size();

        if (currentSize < 2 || previousSize < 2) {
            return new FitnessSummary.RpeTrend(
                    null, null, "unknown",
                    new FitnessSummary.SampleSize(currentSize, previousSize));
        }

        String currentLabel  = modalRating(current);
        String previousLabel = modalRating(previous);
        String trend         = trendLabel(currentLabel, previousLabel);

        return new FitnessSummary.RpeTrend(
                currentLabel, previousLabel, trend,
                new FitnessSummary.SampleSize(currentSize, previousSize));
    }

    /**
     * PR activity in the last 4 weeks, derived from {@code pr_events}.
     */
    private FitnessSummary.PrActivity computePrActivity(UUID userId,
                                                         Instant from,
                                                         Instant to) {
        // Count non-superseded PRs in window (queries across all partitions)
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM pr_events
                WHERE user_id = ?
                  AND created_at >= ?
                  AND created_at <  ?
                  AND superseded_at IS NULL
                """,
                Integer.class, userId, from, to);
        int prCount = count != null ? count : 0;

        // Days since most recent PR ever (all-time)
        java.sql.Timestamp lastPrTs = jdbc.queryForObject("""
                SELECT MAX(created_at) FROM pr_events
                WHERE user_id = ? AND superseded_at IS NULL
                """,
                java.sql.Timestamp.class, userId);

        Integer daysSince = null;
        if (lastPrTs != null) {
            daysSince = (int) ChronoUnit.DAYS.between(lastPrTs.toInstant(), to);
        }

        return new FitnessSummary.PrActivity(prCount, daysSince, FitnessSummary.PrActivity.labelFor(prCount));
    }

    /**
     * Days since each canonical muscle group was last trained (all-time).
     * Muscles never trained are omitted from the map.
     */
    private Map<String, Integer> computeLastTrainedByMuscle(UUID userId, Instant asOf) {
        List<Object[]> rows = jdbc.query("""
                SELECT e.muscle_group, MAX(ws.finished_at) AS last_trained
                FROM set_logs sl
                JOIN workout_sessions ws ON sl.session_id = ws.id
                JOIN exercises e         ON sl.exercise_id = e.id
                WHERE ws.user_id = ?
                  AND ws.status  = 'COMPLETED'
                GROUP BY e.muscle_group
                """,
                (rs, row) -> new Object[]{ rs.getString("muscle_group"), rs.getTimestamp("last_trained") },
                userId);

        // Accumulate: for canonical groups with multiple DB variants, take the most recent
        Map<String, Instant> latestPerMuscle = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String canonical = MuscleGroupUtil.canonicalize((String) row[0]);
            if (canonical.isEmpty()) continue;
            java.sql.Timestamp ts = (java.sql.Timestamp) row[1];
            if (ts == null) continue;
            Instant t = ts.toInstant();
            latestPerMuscle.merge(canonical, t, (a, b) -> a.isAfter(b) ? a : b);
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        latestPerMuscle.forEach((muscle, lastTime) -> {
            int days = (int) ChronoUnit.DAYS.between(lastTime, asOf);
            result.put(muscle, Math.max(0, days));
        });
        return result;
    }

    // ── RPE trend helpers ─────────────────────────────────────────────

    /**
     * Modal rating (most common) in a list of feedback entries.
     * Tie-break: returns the harder (higher-ordinal) rating.
     *
     * @return lowercase label, e.g. "hard"; null if list is empty
     */
    String modalRating(List<SessionFeedback> feedbacks) {
        if (feedbacks.isEmpty()) return null;

        Map<String, Long> counts = feedbacks.stream()
                .map(f -> f.getRating().toUpperCase())
                .filter(RATING_ORDER::contains)
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Long>, Long>comparing(Map.Entry::getValue)
                        .thenComparingInt(e -> RATING_ORDER.indexOf(e.getKey())))
                .map(e -> e.getKey().toLowerCase())
                .orElse(null);
    }

    /**
     * Derive trend label from two modal rating labels.
     *
     * @return "climbing" | "flat" | "dropping" | "unknown"
     */
    String trendLabel(String currentLabel, String previousLabel) {
        if (currentLabel == null || previousLabel == null) return "unknown";
        int cur  = RATING_ORDER.indexOf(currentLabel.toUpperCase());
        int prev = RATING_ORDER.indexOf(previousLabel.toUpperCase());
        if (cur < 0 || prev < 0) return "unknown";
        if (cur > prev) return "climbing";
        if (cur < prev) return "dropping";
        return "flat";
    }

    // ── Nightly job helpers ───────────────────────────────────────────

    /**
     * All distinct user IDs that completed at least one session in the last 60 days.
     */
    private List<UUID> findActiveUserIds(Instant asOf) {
        Instant since60d = asOf.minus(60, ChronoUnit.DAYS);
        return jdbc.queryForList("""
                SELECT DISTINCT user_id FROM workout_sessions
                WHERE status = 'COMPLETED'
                  AND finished_at >= ?
                """,
                UUID.class, since60d);
    }

    /**
     * Human-readable sample window covering all windows used in computation.
     * Earliest start = asOf − 56 days (rpeTrend previous window).
     * Latest = asOf date.
     */
    private String buildSampleWindow(Instant asOf) {
        LocalDate start = LocalDate.ofInstant(asOf.minus(56, ChronoUnit.DAYS), ZoneOffset.UTC);
        LocalDate end   = LocalDate.ofInstant(asOf, ZoneOffset.UTC);
        return start.format(DATE_FMT) + " to " + end.format(DATE_FMT);
    }
}
