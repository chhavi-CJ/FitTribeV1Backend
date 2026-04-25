package com.fittribe.api.findings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fittribe.api.util.Zones;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a {@link WeekData} snapshot for a given user and week.
 *
 * This is the ONLY layer allowed to hit the database for the weekly
 * findings engine. Once it returns a {@link WeekData}, everything
 * downstream (FindingsRule, FindingsGenerator, VerdictGenerator) must
 * work off that snapshot alone — which is what makes all the rules
 * unit-testable without a DB.
 *
 * <h3>Data sources</h3>
 * <ul>
 *   <li>{@code users.weekly_goal} + {@code display_name} — goal + first name</li>
 *   <li>{@code workout_sessions.exercises} JSONB — per-exercise sets for COMPLETED sessions
 *       in {@code [weekStart, weekEnd)}. This is authoritative for this-week data.</li>
 *   <li>{@code workout_sessions.ai_planned_weights} JSONB — plan target per exercise</li>
 *   <li>{@code set_logs} (joined via {@code workout_sessions}) —
 *       <em>only</em> for previous-week top sets (PR regression input) and
 *       all-time previous max (PR "previous best" lookup)</li>
 *   <li>{@code exercises} catalog — muscle_group, secondary_muscles, is_bodyweight</li>
 * </ul>
 *
 * <h3>Week window convention</h3>
 * Weeks are UTC Monday → next UTC Monday. Passing {@code weekStart =
 * 2026-04-06} (Monday) yields {@code weekEnd = 2026-04-13}. All session
 * filtering uses {@code workout_sessions.finished_at} (not started_at)
 * so a Monday-started session that ran past midnight still belongs to
 * the correct week.
 */
@Component
public class WeekDataBuilder {

    private static final Logger log = LoggerFactory.getLogger(WeekDataBuilder.class);

    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository setLogRepo;
    private final ExerciseRepository exerciseRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper;

    @Autowired
    public WeekDataBuilder(WorkoutSessionRepository sessionRepo,
                           SetLogRepository setLogRepo,
                           ExerciseRepository exerciseRepo,
                           UserRepository userRepo,
                           ObjectMapper objectMapper) {
        this.sessionRepo = sessionRepo;
        this.setLogRepo = setLogRepo;
        this.exerciseRepo = exerciseRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds a {@link WeekData} for the week starting on {@code weekStart}
     * (UTC Monday). The returned object is fully populated and wrapped in
     * unmodifiable views — callers (rules) cannot mutate it.
     *
     * Returns an almost-empty {@link WeekData} (zero sessions, no PRs,
     * empty maps) if the user had no COMPLETED sessions in the window —
     * that's a valid state the rules must handle (e.g. LowAdherenceRule
     * fires on missed sessions).
     */
    public WeekData build(UUID userId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(7);
        Instant weekStartInstant = weekStart.atStartOfDay(Zones.APP_ZONE).toInstant();
        Instant weekEndInstant   = weekEnd.atStartOfDay(Zones.APP_ZONE).toInstant();

        // ── 1. User (weekly goal + first name) ─────────────────────────
        User user = userRepo.findById(userId).orElse(null);
        int sessionsGoal = (user != null && user.getWeeklyGoal() != null)
                ? user.getWeeklyGoal()
                : 4;
        String userFirstName = extractFirstName(user);

        // ── 2. This week's COMPLETED sessions ──────────────────────────
        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndFinishedAtBetween(
                        userId, "COMPLETED", weekStartInstant, weekEndInstant);
        sessions.sort((a, b) -> a.getFinishedAt().compareTo(b.getFinishedAt()));

        // ── 3. Exercise catalog snapshot (cheap: ~19 rows today) ───────
        Map<String, Exercise> catalogByIdRaw = new HashMap<>();
        for (Exercise ex : exerciseRepo.findAll()) {
            catalogByIdRaw.put(ex.getId(), ex);
        }

        // Builder-local mutable accumulators — wrapped unmodifiable at the end
        List<WeekData.SessionSummary> sessionSummaries = new ArrayList<>();
        Map<String, List<WeekData.LoggedSet>> setsByExercise = new LinkedHashMap<>();
        Map<WeeklyReportMuscle, Set<UUID>> musclesHitToSessionIds = new EnumMap<>(WeeklyReportMuscle.class);
        int pushSessionCount = 0;
        int pullSessionCount = 0;
        int legsSessionCount = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        Set<String> prExerciseIdsThisWeek = new LinkedHashSet<>();
        Map<String, WeekData.TargetVsLogged> targetExercises = new LinkedHashMap<>();

        // Needed only to build the catalog snapshot at the end: union of
        // every exercise_id touched this week (via logged sets OR via
        // ai_planned_weights, since a planned-but-skipped exercise still
        // matters for TooLight / adherence rules).
        Set<String> touchedExerciseIds = new LinkedHashSet<>();

        // ── 4. Iterate sessions, parse both JSONB columns ──────────────
        for (WorkoutSession ws : sessions) {
            BigDecimal vol = ws.getTotalVolumeKg();
            if (vol != null) totalVolume = totalVolume.add(vol);

            List<Map<String, Object>> parsedExercises = parseExercisesJson(ws);
            List<Map<String, Object>> parsedPlanned = parsePlannedWeightsJson(ws);

            // Planned targets: record the first target we see per exercise
            // (plan targets within a week for one exercise are usually
            // identical; if they're not, the earliest session's target is
            // the one the user actually tried to hit first).
            for (Map<String, Object> pw : parsedPlanned) {
                String exerciseId = stringOf(pw.get("exerciseId"));
                if (exerciseId == null) continue;
                BigDecimal target = bigDecimalOf(pw.get("suggestedKg"));
                if (target == null) continue;
                touchedExerciseIds.add(exerciseId);
                targetExercises.computeIfAbsent(exerciseId, id ->
                        new WeekData.TargetVsLogged(id, target, null));
            }

            // Per-session push/pull/legs classification — bucketed by
            // volume across classifications, then the max bucket wins.
            Map<WeekData.ExerciseMeta.PushPull, BigDecimal> volumeByClass =
                    new EnumMap<>(WeekData.ExerciseMeta.PushPull.class);
            Set<WeeklyReportMuscle> musclesTrainedInSession =
                    EnumSetFactory.empty();

            for (Map<String, Object> ex : parsedExercises) {
                String exerciseId = stringOf(ex.get("exerciseId"));
                if (exerciseId == null) continue;
                touchedExerciseIds.add(exerciseId);

                BigDecimal exVolume = bigDecimalOf(ex.get("totalVolume"));
                BigDecimal exMaxKg  = bigDecimalOf(ex.get("maxWeightKg"));
                boolean exIsPr      = Boolean.TRUE.equals(ex.get("isPr"));

                if (exIsPr) prExerciseIdsThisWeek.add(exerciseId);

                // Update TargetVsLogged.loggedMaxKg if this exercise has a target
                targetExercises.computeIfPresent(exerciseId, (id, existing) -> {
                    BigDecimal newLoggedMax = existing.loggedMaxKg() == null
                            ? exMaxKg
                            : (exMaxKg != null && exMaxKg.compareTo(existing.loggedMaxKg()) > 0
                                    ? exMaxKg
                                    : existing.loggedMaxKg());
                    return new WeekData.TargetVsLogged(id, existing.targetKg(), newLoggedMax);
                });

                // Catalog lookup for push/pull + muscle coverage
                Exercise catalog = catalogByIdRaw.get(exerciseId);
                String muscleGroup = catalog != null ? catalog.getMuscleGroup() : null;
                String[] secondaries = catalog != null ? catalog.getSecondaryMuscles() : null;

                // Muscles trained (smart-filter)
                Set<WeeklyReportMuscle> exerciseTiles = MuscleMapper.musclesFor(muscleGroup, secondaries);
                musclesTrainedInSession.addAll(exerciseTiles);

                // Push/pull classification and volume contribution
                WeekData.ExerciseMeta.PushPull cls = classifyPushPull(muscleGroup);
                BigDecimal contribution = exVolume != null ? exVolume : BigDecimal.ZERO;
                volumeByClass.merge(cls, contribution, BigDecimal::add);

                // Extract individual sets
                Object setsRaw = ex.get("sets");
                if (setsRaw instanceof List<?> setsList) {
                    List<WeekData.LoggedSet> acc = setsByExercise.computeIfAbsent(
                            exerciseId, k -> new ArrayList<>());
                    for (Object setObj : setsList) {
                        if (!(setObj instanceof Map<?, ?> setMap)) continue;
                        BigDecimal wKg = bigDecimalOf(setMap.get("weightKg"));
                        Integer reps   = intOf(setMap.get("reps"));
                        Integer setNo  = intOf(setMap.get("setNumber"));
                        if (reps == null || setNo == null) continue;
                        acc.add(new WeekData.LoggedSet(
                                exerciseId,
                                wKg != null ? wKg : BigDecimal.ZERO,
                                reps,
                                setNo,
                                exIsPr  // coarse: set marked PR if the parent exercise is PR
                        ));
                    }
                }
            }

            // Register muscles hit → sessionId for sessionsByMuscle count
            for (WeeklyReportMuscle tile : musclesTrainedInSession) {
                musclesHitToSessionIds
                        .computeIfAbsent(tile, k -> new HashSet<>())
                        .add(ws.getId());
            }

            // Determine dominant classification for this session
            WeekData.ExerciseMeta.PushPull dominant = dominantClass(volumeByClass);
            boolean isPush = dominant == WeekData.ExerciseMeta.PushPull.PUSH;
            boolean isPull = dominant == WeekData.ExerciseMeta.PushPull.PULL;
            boolean isLegs = dominant == WeekData.ExerciseMeta.PushPull.LEGS;
            if (isPush) pushSessionCount++;
            if (isPull) pullSessionCount++;
            if (isLegs) legsSessionCount++;

            sessionSummaries.add(new WeekData.SessionSummary(
                    ws.getId(),
                    ws.getFinishedAt(),
                    vol != null ? vol : BigDecimal.ZERO,
                    Collections.unmodifiableSet(musclesTrainedInSession),
                    isPush, isPull, isLegs
            ));
        }

        // ── 5. thisWeekTopSets from setsByExercise ─────────────────────
        Map<String, WeekData.TopSet> thisWeekTopSets = new LinkedHashMap<>();
        for (Map.Entry<String, List<WeekData.LoggedSet>> e : setsByExercise.entrySet()) {
            WeekData.TopSet top = e.getValue().stream()
                    .filter(s -> s.weightKg() != null)
                    .reduce(null, (acc, s) -> {
                        if (acc == null) return new WeekData.TopSet(s.weightKg(), s.reps());
                        int cmp = s.weightKg().compareTo(acc.weightKg());
                        if (cmp > 0) return new WeekData.TopSet(s.weightKg(), s.reps());
                        if (cmp == 0 && s.reps() > acc.reps()) return new WeekData.TopSet(s.weightKg(), s.reps());
                        return acc;
                    }, (a, b) -> a); // not used (sequential stream)
            if (top != null) thisWeekTopSets.put(e.getKey(), top);
        }

        // ── 6. previousWeekTopSets via set_logs ────────────────────────
        Instant prevStartInstant = weekStart.minusDays(7).atStartOfDay(Zones.APP_ZONE).toInstant();
        Instant prevEndInstant = weekStartInstant;
        Map<String, WeekData.TopSet> previousWeekTopSets = new LinkedHashMap<>();
        List<Object[]> prevRows = setLogRepo.findTopSetsPerExerciseInWindow(
                userId, prevStartInstant, prevEndInstant);
        for (Object[] row : prevRows) {
            String exerciseId = (String) row[0];
            BigDecimal w = (BigDecimal) row[1];
            Integer reps = ((Number) row[2]).intValue();
            if (exerciseId != null && w != null) {
                previousWeekTopSets.put(exerciseId, new WeekData.TopSet(w, reps));
            }
        }

        // ── 7. personalRecords — PRs this week + previous all-time max ─
        List<WeekData.PrEntry> personalRecords = new ArrayList<>();
        if (!prExerciseIdsThisWeek.isEmpty()) {
            // Previous all-time max per exercise (strictly before weekStart)
            Map<String, BigDecimal> previousMaxByExercise = new HashMap<>();
            List<Object[]> prevMaxRows = setLogRepo.findAllTimeMaxBeforeForExercises(
                    userId, weekStartInstant, prExerciseIdsThisWeek);
            for (Object[] row : prevMaxRows) {
                String exerciseId = (String) row[0];
                BigDecimal maxW = (BigDecimal) row[1];
                if (exerciseId != null && maxW != null) {
                    previousMaxByExercise.put(exerciseId, maxW);
                }
            }

            // Build PR entries from thisWeekTopSets (new max) + previousMaxByExercise
            for (String exerciseId : prExerciseIdsThisWeek) {
                WeekData.TopSet thisTop = thisWeekTopSets.get(exerciseId);
                if (thisTop == null) continue;
                BigDecimal previousMax = previousMaxByExercise.get(exerciseId);
                personalRecords.add(new WeekData.PrEntry(
                        exerciseId,
                        thisTop.weightKg(),
                        previousMax,  // nullable: first-ever PR
                        thisTop.reps()
                ));
            }
        }

        // ── 8. Catalog snapshot for rules ──────────────────────────────
        Map<String, WeekData.ExerciseMeta> exerciseCatalog = new LinkedHashMap<>();
        for (String exerciseId : touchedExerciseIds) {
            Exercise catalog = catalogByIdRaw.get(exerciseId);
            if (catalog == null) {
                // Exercise not in catalog — still record a stub so rules
                // don't NPE. Classification falls through to OTHER.
                exerciseCatalog.put(exerciseId, new WeekData.ExerciseMeta(
                        exerciseId, exerciseId, null, null, false,
                        WeekData.ExerciseMeta.PushPull.OTHER));
                continue;
            }
            exerciseCatalog.put(exerciseId, new WeekData.ExerciseMeta(
                    catalog.getId(),
                    catalog.getName(),
                    catalog.getMuscleGroup(),
                    catalog.getSecondaryMuscles(),
                    catalog.isBodyweight(),
                    classifyPushPull(catalog.getMuscleGroup())
            ));
        }

        // ── 9. sessionsByMuscle (tile → distinct session count) ────────
        Map<WeeklyReportMuscle, Integer> sessionsByMuscle = new EnumMap<>(WeeklyReportMuscle.class);
        for (WeeklyReportMuscle tile : WeeklyReportMuscle.values()) {
            Set<UUID> ids = musclesHitToSessionIds.get(tile);
            sessionsByMuscle.put(tile, ids == null ? 0 : ids.size());
        }

        // ── 10. Headline values ────────────────────────────────────────
        int sessionsLogged = sessions.size();
        boolean weeklyGoalHit = sessionsLogged >= sessionsGoal;
        int weekNumber = deriveWeekNumber(sessions);
        boolean isWeekOne = weekNumber == 1;
        int prCount = prExerciseIdsThisWeek.size();

        // ── 11. Freeze into an unmodifiable WeekData ───────────────────
        return new WeekData(
                userId,
                weekStart,
                weekEnd,
                weekNumber,
                isWeekOne,
                userFirstName,
                sessionsLogged,
                sessionsGoal,
                weeklyGoalHit,
                totalVolume,
                prCount,
                Collections.unmodifiableList(sessionSummaries),
                freezeListValueMap(setsByExercise),
                Collections.unmodifiableMap(sessionsByMuscle),
                pushSessionCount,
                pullSessionCount,
                legsSessionCount,
                Collections.unmodifiableMap(thisWeekTopSets),
                Collections.unmodifiableMap(previousWeekTopSets),
                Collections.unmodifiableList(personalRecords),
                Collections.unmodifiableMap(targetExercises),
                Collections.unmodifiableMap(exerciseCatalog)
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private List<Map<String, Object>> parseExercisesJson(WorkoutSession ws) {
        String json = ws.getExercises();
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not parse exercises JSONB for session {}: {}",
                    ws.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parsePlannedWeightsJson(WorkoutSession ws) {
        String json = ws.getAiPlannedWeights();
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not parse ai_planned_weights for session {}: {}",
                    ws.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private static WeekData.ExerciseMeta.PushPull classifyPushPull(String muscleGroup) {
        if (muscleGroup == null) return WeekData.ExerciseMeta.PushPull.OTHER;
        return switch (muscleGroup) {
            case "CHEST", "SHOULDERS", "TRICEPS" -> WeekData.ExerciseMeta.PushPull.PUSH;
            case "BACK", "BICEPS" -> WeekData.ExerciseMeta.PushPull.PULL;
            case "LEGS", "HAMSTRINGS", "GLUTES", "CALVES" -> WeekData.ExerciseMeta.PushPull.LEGS;
            default -> WeekData.ExerciseMeta.PushPull.OTHER;
        };
    }

    /**
     * Returns the classification with the highest total volume in this
     * session, or OTHER if everything tied / no volume logged. Ties
     * between non-OTHER classes are broken in the order PUSH > PULL > LEGS
     * to keep the result deterministic — this only bites in pathological
     * cases (exactly equal volume across push and pull sets) which are
     * vanishingly rare in real data.
     */
    private static WeekData.ExerciseMeta.PushPull dominantClass(
            Map<WeekData.ExerciseMeta.PushPull, BigDecimal> volumeByClass) {
        WeekData.ExerciseMeta.PushPull best = WeekData.ExerciseMeta.PushPull.OTHER;
        BigDecimal bestVolume = BigDecimal.ZERO;
        for (WeekData.ExerciseMeta.PushPull cls : List.of(
                WeekData.ExerciseMeta.PushPull.PUSH,
                WeekData.ExerciseMeta.PushPull.PULL,
                WeekData.ExerciseMeta.PushPull.LEGS)) {
            BigDecimal v = volumeByClass.getOrDefault(cls, BigDecimal.ZERO);
            if (v.compareTo(bestVolume) > 0) {
                best = cls;
                bestVolume = v;
            }
        }
        return best;
    }

    private static int deriveWeekNumber(List<WorkoutSession> sessions) {
        for (WorkoutSession ws : sessions) {
            if (ws.getWeekNumber() != null && ws.getWeekNumber() > 0) {
                return ws.getWeekNumber();
            }
        }
        return 1;
    }

    private static String extractFirstName(User user) {
        if (user == null || user.getDisplayName() == null) return "there";
        String name = user.getDisplayName().trim();
        if (name.isEmpty()) return "there";
        int sp = name.indexOf(' ');
        return sp == -1 ? name : name.substring(0, sp);
    }

    private static BigDecimal bigDecimalOf(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s && !s.isBlank()) return new BigDecimal(s);
        return null;
    }

    private static Integer intOf(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && !s.isBlank()) return Integer.parseInt(s);
        return null;
    }

    private static String stringOf(Object o) {
        return o == null ? null : o.toString();
    }

    private static <K, V> Map<K, List<V>> freezeListValueMap(Map<K, List<V>> in) {
        Map<K, List<V>> frozen = new LinkedHashMap<>();
        for (Map.Entry<K, List<V>> e : in.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * Tiny wrapper so the EnumSet declaration site reads as
     * {@code EnumSetFactory.empty()} without a generic-typing dance at
     * each call site. Keeps {@code build()} readable.
     */
    private static final class EnumSetFactory {
        static Set<WeeklyReportMuscle> empty() {
            return java.util.EnumSet.noneOf(WeeklyReportMuscle.class);
        }
    }
}
