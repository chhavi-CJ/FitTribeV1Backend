package com.fittribe.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.util.MuscleGroupUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Provides recent set history for AI plan generation as {@link HistoricalSet} records.
 *
 * <p>Reads from {@code workout_sessions.exercises} JSONB and cross-references
 * {@code pr_events} for per-set isPr flags. {@code set_logs} is no longer read
 * by this service post-Flyway-V44 (PR System V2) — the JSONB snapshot captured
 * at session finish is the durable source of truth for plan generation history.
 */
@Service
public class PlanHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PlanHistoryService.class);

    private final WorkoutSessionRepository sessionRepo;
    private final PrEventRepository        prEventRepo;
    private final ObjectMapper             objectMapper;

    public PlanHistoryService(WorkoutSessionRepository sessionRepo,
                               PrEventRepository prEventRepo,
                               ObjectMapper objectMapper) {
        this.sessionRepo  = sessionRepo;
        this.prEventRepo  = prEventRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns all logged sets for a user's completed sessions since the given instant,
     * sourced from the JSONB snapshot in {@code workout_sessions.exercises}.
     *
     * @param userId the user whose history to load
     * @param since  lower bound for session {@code finishedAt} (inclusive)
     * @param exMap  exercise catalogue keyed by exerciseId — used to resolve
     *               canonical {@code muscleGroup}
     * @return flat list of {@link HistoricalSet}, one entry per logged set;
     *         empty list when no completed sessions exist in the window
     */
    public List<HistoricalSet> getRecentLoggedSets(UUID userId, Instant since,
                                                    Map<String, Exercise> exMap) {
        // 3a) Fetch completed sessions in window
        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndFinishedAtBetween(userId, "COMPLETED", since, Instant.now());
        if (sessions.isEmpty()) return List.of();

        // 3b) Build sessionIds list and weekStarts set in one pass
        List<UUID> sessionIds = new ArrayList<>(sessions.size());
        Set<LocalDate> weekStarts = new HashSet<>();
        for (WorkoutSession s : sessions) {
            if (s.getFinishedAt() == null) continue;
            sessionIds.add(s.getId());
            weekStarts.add(weekStartFor(s.getFinishedAt()));
        }
        if (sessionIds.isEmpty()) return List.of();

        // 3c) Cross-reference pr_events to find PR set IDs.
        // FIRST_EVER excluded — first-ever attempts are benchmarks, not progress.
        List<PrEvent> prEvents = prEventRepo
                .findByUserIdAndSessionIdInAndWeekStartInAndSupersededAtIsNull(
                        userId, sessionIds, weekStarts);
        Set<UUID> prSetIds = new HashSet<>();
        Set<String> prCategories = Set.of("WEIGHT_PR", "REP_PR", "VOLUME_PR", "MAX_ATTEMPT");
        for (PrEvent pe : prEvents) {
            if (pe.getSetId() != null && prCategories.contains(pe.getPrCategory())) {
                prSetIds.add(pe.getSetId());
            }
        }

        // 3d) Parse each session's JSONB and emit HistoricalSet records
        List<HistoricalSet> result = new ArrayList<>();
        TypeReference<List<Map<String, Object>>> listOfMaps = new TypeReference<>() {};

        for (WorkoutSession s : sessions) {
            String exercisesJson = s.getExercises();
            if (exercisesJson == null || exercisesJson.isBlank()) continue;

            List<Map<String, Object>> exercises;
            try {
                exercises = objectMapper.readValue(exercisesJson, listOfMaps);
            } catch (Exception e) {
                log.warn("PlanHistoryService: malformed exercises JSONB on session {}, skipping — {}",
                        s.getId(), e.getMessage());
                continue;
            }

            for (Map<String, Object> ex : exercises) {
                String exerciseId   = (String) ex.get("exerciseId");
                String exerciseName = (String) ex.getOrDefault("exerciseName", exerciseId);
                String muscleGroup  = resolveMuscleGroupFromCatalog(exerciseId, exMap);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sets =
                        (List<Map<String, Object>>) ex.get("sets");
                if (sets == null) continue;

                for (Map<String, Object> set : sets) {
                    UUID       setId     = parseUuid(set.get("setId"));
                    Integer    setNumber = toInt(set.get("setNumber"));
                    Integer    reps      = toInt(set.get("reps"));
                    BigDecimal weightKg  = toBigDecimal(set.get("weightKg"));
                    boolean    isPr      = setId != null && prSetIds.contains(setId);

                    result.add(new HistoricalSet(
                            s.getId(), s.getFinishedAt(),
                            exerciseId, exerciseName, muscleGroup,
                            weightKg, reps, setNumber, setId, isPr));
                }
            }
        }

        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private LocalDate weekStartFor(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String resolveMuscleGroupFromCatalog(String exerciseId,
                                                  Map<String, Exercise> exMap) {
        if (exerciseId == null) return "";
        Exercise ex = exMap.get(exerciseId);
        if (ex == null) return "";
        String raw = ex.getMuscleGroup();
        if (raw == null || raw.isBlank()) return "";
        return MuscleGroupUtil.canonicalize(raw);
    }

    private UUID parseUuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
