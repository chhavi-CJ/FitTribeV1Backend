package com.fittribe.api.strengthscore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserProgressSnapshot;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeekDataBuilder;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.StrengthScoreHistoryRepository;
import com.fittribe.api.repository.UserProgressSnapshotRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.jobs.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrator for computing and persisting weekly strength scores per muscle.
 *
 * <h3>Workflow</h3>
 * On session finish, this service:
 * <ol>
 *   <li>Loads the session and user profile (bodyweight, gender, fitness level)</li>
 *   <li>Fetches the entire week's session data via WeekDataBuilder</li>
 *   <li>For each exercise logged in the triggering session:
 *     <ul>
 *       <li>Filters out exercises with < 2 sets in the current week</li>
 *       <li>Finds the top set (by Epley 1RM) and estimates 1RM</li>
 *       <li>Computes a 0–100 strength score via StrengthScoreService</li>
 *       <li>Maps the exercise muscle group to TrendsMuscle (6-muscle taxonomy)</li>
 *       <li>Skips CORE and FULL_BODY exercises (no mapping)</li>
 *     </ul>
 *   </li>
 *   <li>Aggregates scores by muscle: average for each TrendsMuscle bucket</li>
 *   <li>Upserts strength_score_history rows (one per muscle with qualifying exercises)</li>
 *   <li>Builds a user_progress_snapshot JSONB payload with muscle scores and overall average</li>
 *   <li>Upserts user_progress_snapshot</li>
 * </ol>
 *
 * <h3>Transaction isolation</h3>
 * Uses {@link TransactionTemplate} explicitly (NOT class-level {@code @Transactional})
 * so the long-running computation (WeekDataBuilder, repository queries) doesn't hold
 * a database connection unnecessarily. The caller wraps this service in try/catch
 * to isolate failures from the session finish response.
 */
@Service
public class ProgressSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(ProgressSnapshotService.class);

    private final WorkoutSessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final ExerciseRepository exerciseRepo;
    private final StrengthScoreService scoreService;
    private final StrengthScoreHistoryRepository historyRepo;
    private final UserProgressSnapshotRepository snapshotRepo;
    private final WeekDataBuilder weekDataBuilder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ProgressSnapshotService(
            WorkoutSessionRepository sessionRepo,
            UserRepository userRepo,
            ExerciseRepository exerciseRepo,
            StrengthScoreService scoreService,
            StrengthScoreHistoryRepository historyRepo,
            UserProgressSnapshotRepository snapshotRepo,
            WeekDataBuilder weekDataBuilder,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.exerciseRepo = exerciseRepo;
        this.scoreService = scoreService;
        this.historyRepo = historyRepo;
        this.snapshotRepo = snapshotRepo;
        this.weekDataBuilder = weekDataBuilder;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Compute and persist strength scores for a given user's week.
     *
     * <p>Scoped to a specific week (Monday–Sunday, IST) regardless of which
     * session triggered the compute. Useful both for:
     * <ul>
     *   <li>SessionController hook on every session finish (compute weekStart
     *       from session.getFinishedAt())</li>
     *   <li>Sunday cron batch (compute weekStart from job payload)</li>
     * </ul>
     *
     * <p>Long-running (queries the entire week of data). Failures don't
     * propagate to caller; the caller is responsible for try/catch isolation.
     *
     * @param userId the user whose snapshot to compute
     * @param weekStart the Monday (ISO 8601) of the target week (IST)
     * @throws IllegalArgumentException if the user doesn't exist
     * @throws Exception on JSON serialization or database errors
     */
    public void computeForUserWeek(UUID userId, LocalDate weekStart) {
        // ── Load user profile ──────────────────────────────────────────
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // ── Fetch entire week's session data ───────────────────────────
        WeekData weekData = weekDataBuilder.build(userId, weekStart);

        // ── Extract exercise IDs from the week's data ───────────────────
        List<String> exerciseIds = new ArrayList<>(weekData.setsByExercise().keySet());
        if (exerciseIds.isEmpty()) {
            log.debug("Week starting {} has no exercises for user {}; skipping strength snapshot",
                    weekStart, userId);
            return;
        }

        // ── Load all exercises in one call (avoid N+1) ──────────────────
        Map<String, Exercise> exercisesById = exerciseRepo.findAllById(exerciseIds)
                .stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));

        // ── Per-muscle score aggregation ───────────────────────────────
        Map<TrendsMuscle, List<Integer>> muscleScores = new HashMap<>();
        for (TrendsMuscle m : TrendsMuscle.values()) {
            muscleScores.put(m, new java.util.ArrayList<>());
        }

        // ── Loop through session exercises ─────────────────────────────
        for (String exerciseId : exerciseIds) {
            // Get the week's sets for this exercise
            List<WeekData.LoggedSet> allSetsThisWeek = weekData.setsByExercise()
                    .getOrDefault(exerciseId, List.of());

            // Filter: < 2 sets in the week → skip
            if (allSetsThisWeek.size() < 2) {
                log.debug("Exercise {} has < 2 sets in week; skipping from score", exerciseId);
                continue;
            }

            // Find top set (max Epley 1RM)
            WeekData.LoggedSet topSet = allSetsThisWeek.stream()
                    .max((s1, s2) -> Double.compare(
                            scoreService.computeEpley1RM(s1.weightKg().doubleValue(), s1.reps()),
                            scoreService.computeEpley1RM(s2.weightKg().doubleValue(), s2.reps())))
                    .orElse(null);

            if (topSet == null) {
                continue;
            }

            // Compute Epley 1RM and exercise score
            double epley1RM = scoreService.computeEpley1RM(topSet.weightKg().doubleValue(), topSet.reps());
            Exercise exercise = exercisesById.get(exerciseId);
            if (exercise == null) {
                log.warn("Exercise {} not found in catalog; skipping", exerciseId);
                continue;
            }

            int exerciseScore = scoreService.computeExerciseScore(
                    exerciseId,
                    epley1RM,
                    user.getWeightKg().doubleValue(),
                    user.getGender(),
                    user.getFitnessLevel());

            // Resolve muscle mapping (filter: null mapping → skip)
            TrendsMuscle muscle = TrendsMuscleMapper.map(exercise.getMuscleGroup());
            if (muscle == null) {
                log.debug("Exercise {} maps to null TrendsMuscle (CORE/FULL_BODY); skipping",
                        exerciseId);
                continue;
            }

            // Add to bucket
            muscleScores.get(muscle).add(exerciseScore);
        }

        // ── Aggregate and persist ──────────────────────────────────────
        transactionTemplate.executeWithoutResult(status -> {
            // Upsert strength_score_history rows (one per muscle with qualifying exercises)
            for (TrendsMuscle muscle : TrendsMuscle.values()) {
                List<Integer> scores = muscleScores.get(muscle);
                if (scores.isEmpty()) {
                    // Don't write zero rows for muscles with no data
                    continue;
                }

                int muscleScore = (int) Math.round(scores.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0));

                historyRepo.upsert(userId, muscle.name(), weekStart, muscleScore, 1);
            }

            // Build and upsert user_progress_snapshot
            Map<String, Object> snapshotData = buildSnapshotPayload(muscleScores);
            String snapshotJson = serializeToJson(snapshotData);
            snapshotRepo.upsert(userId, snapshotJson, 1);
        });

        log.info("Computed strength snapshot for user {} week {}", userId, weekStart);
    }

    /**
     * Extract exerciseIds from the session's exercises JSONB.
     */
    private List<String> parseExerciseIds(String exercisesJson) {
        try {
            if (exercisesJson == null || exercisesJson.isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> exercises = objectMapper.readValue(exercisesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return exercises.stream()
                    .filter(e -> e.get("exerciseId") != null)
                    .map(e -> e.get("exerciseId").toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to parse exercises JSON; returning empty list", e);
            return List.of();
        }
    }

    /**
     * Build the JSONB payload for user_progress_snapshot.
     */
    private Map<String, Object> buildSnapshotPayload(Map<TrendsMuscle, List<Integer>> muscleScores) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // lastUpdated
        payload.put("lastUpdated", Instant.now().toString());

        // muscleScores: only include muscles with qualifying exercises
        Map<String, Integer> muscleScoresMap = new LinkedHashMap<>();
        List<Integer> allScores = new java.util.ArrayList<>();
        for (TrendsMuscle muscle : TrendsMuscle.values()) {
            List<Integer> scores = muscleScores.get(muscle);
            if (!scores.isEmpty()) {
                int avg = (int) Math.round(scores.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0));
                muscleScoresMap.put(muscle.name(), avg);
                allScores.add(avg);
            }
        }
        payload.put("muscleScores", muscleScoresMap);

        // overallScore: average of all populated muscles
        int overallScore = allScores.isEmpty() ? 0
                : (int) Math.round(allScores.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0));
        payload.put("overallScore", overallScore);

        // formulaVersion
        payload.put("formulaVersion", 1);

        return payload;
    }

    /**
     * Serialize an object to JSON via the shared ObjectMapper.
     */
    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize snapshot payload to JSON", e);
            throw new RuntimeException(e);
        }
    }
}
