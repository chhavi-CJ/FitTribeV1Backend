package com.fittribe.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recovery gate for the bonus session pipeline.
 *
 * For a given user, computes which canonical muscle groups are COOKED
 * (under 48hr since last trained), READY (48-72hr), or FRESH (72hr+).
 *
 * Muscle groups that have never been trained are implicitly FRESH —
 * they are absent from the returned map entirely, and callers should
 * treat "not in map" as FRESH.
 *
 * Canonical muscle groups are the 11 uppercase strings from
 * exercises.muscle_group: CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
 * LEGS, HAMSTRINGS, GLUTES, CALVES, CORE, FULL_BODY.
 *
 * FULL_BODY exercises (burpees, kettlebell swing, power clean etc.)
 * do NOT contribute to FULL_BODY recovery tracking. Instead their
 * secondary_muscles are used, so a logged burpees session counts as
 * mild hits on CHEST/SHOULDERS/LEGS via the secondary array rather
 * than as a single FULL_BODY entry. This matches how coaches reason
 * about recovery: no single "full body" muscle exists physiologically.
 *
 * Thresholds are flat for MVP — 48hr and 72hr for every muscle group.
 * Per-muscle thresholds (BICEPS recovers faster than LEGS) are a
 * future refinement once we have user data to tune against.
 *
 * Scans the last 14 days of completed sessions. A user who skipped
 * a full training week will still get correct COOKED/READY flags for
 * muscles trained on day 13, but muscles untrained for 14+ days are
 * treated as FRESH, which is correct — they are fully recovered.
 *
 * This service is stateless and thread-safe.
 */
@Service
public class RecoveryGateService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryGateService.class);

    private static final int SCAN_WINDOW_DAYS = 14;
    private static final int COOKED_UNTIL_HOURS = 48;
    private static final int READY_UNTIL_HOURS = 72;

    /**
     * The 11 canonical muscle_group values from the exercises catalog.
     * Callers can iterate this list to find muscles absent from a user's
     * recovery map (those muscles are FRESH by implication).
     */
    public static final Set<String> CANONICAL_MUSCLE_GROUPS = Set.of(
            "CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS",
            "LEGS", "HAMSTRINGS", "GLUTES", "CALVES", "CORE", "FULL_BODY"
    );

    public enum RecoveryState {
        /** Under 48hr since last trained — do not train again. */
        COOKED,
        /** 48-72hr since last trained — acceptable but prefer fresher groups. */
        READY,
        /** 72hr+ since last trained — fully recovered. */
        FRESH
    }

    private final WorkoutSessionRepository sessionRepo;
    private final ExerciseRepository exerciseRepo;
    private final ObjectMapper mapper;

    public RecoveryGateService(WorkoutSessionRepository sessionRepo,
                                ExerciseRepository exerciseRepo,
                                ObjectMapper mapper) {
        this.sessionRepo = sessionRepo;
        this.exerciseRepo = exerciseRepo;
        this.mapper = mapper;
    }

    /**
     * Compute the recovery state for every muscle group that has been
     * trained in the last 14 days. Muscle groups not in the map are
     * implicitly FRESH.
     *
     * @param userId the user to compute for
     * @param now    the reference time (usually Instant.now(), injected for testability)
     * @return map of canonical muscle group string to RecoveryState
     */
    public Map<String, RecoveryState> computeRecoveryState(UUID userId, Instant now) {
        Instant scanStart = now.minus(SCAN_WINDOW_DAYS, ChronoUnit.DAYS);

        List<WorkoutSession> recentSessions = sessionRepo
                .findByUserIdAndStatusAndFinishedAtBetween(
                        userId, "COMPLETED", scanStart, now);

        if (recentSessions.isEmpty()) {
            return Map.of();
        }

        // Load exercise catalog once for muscleGroup + secondaryMuscles lookup
        Map<String, Exercise> exerciseById = exerciseRepo.findAll().stream()
                .collect(Collectors.toMap(Exercise::getId, ex -> ex));

        // For each muscle group, track the most recent finished_at that trained it
        Map<String, Instant> lastTrainedAt = new HashMap<>();

        for (WorkoutSession session : recentSessions) {
            if (session.getFinishedAt() == null || session.getExercises() == null) continue;

            Set<String> musclesHitThisSession = extractMuscleGroups(session, exerciseById);

            for (String muscle : musclesHitThisSession) {
                lastTrainedAt.merge(muscle, session.getFinishedAt(),
                        (existing, incoming) -> existing.isAfter(incoming) ? existing : incoming);
            }
        }

        // Convert timestamps to RecoveryState
        Map<String, RecoveryState> result = new HashMap<>();
        for (Map.Entry<String, Instant> entry : lastTrainedAt.entrySet()) {
            long hoursSince = ChronoUnit.HOURS.between(entry.getValue(), now);
            RecoveryState state;
            if (hoursSince < COOKED_UNTIL_HOURS) state = RecoveryState.COOKED;
            else if (hoursSince < READY_UNTIL_HOURS) state = RecoveryState.READY;
            else state = RecoveryState.FRESH;
            result.put(entry.getKey(), state);
        }

        return result;
    }

    /**
     * Extract the set of canonical muscle groups trained in one session.
     *
     * Reads the exercises JSONB (set at session finish), looks each
     * exerciseId up in the catalog, and collects:
     *   - primary muscle_group (unless FULL_BODY — see below)
     *   - all secondary_muscles, mapped to canonical values where possible
     *
     * FULL_BODY primary is skipped; its secondary_muscles still contribute.
     * An exercise with primary=FULL_BODY and no secondaries would contribute
     * nothing, which is correct — it's a composite we don't track at the
     * FULL_BODY level.
     *
     * Unknown exerciseIds (not in catalog) are skipped with a debug log.
     */
    private Set<String> extractMuscleGroups(WorkoutSession session,
                                             Map<String, Exercise> exerciseById) {
        Set<String> muscles = new HashSet<>();

        List<Map<String, Object>> loggedExercises;
        try {
            loggedExercises = mapper.readValue(
                    session.getExercises(), new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Could not parse exercises JSONB for session {}: {}",
                    session.getId(), e.getMessage());
            return muscles;
        }

        for (Map<String, Object> ex : loggedExercises) {
            String exerciseId = (String) ex.get("exerciseId");
            if (exerciseId == null) continue;

            Exercise entity = exerciseById.get(exerciseId);
            if (entity == null) {
                log.debug("Unknown exerciseId in session {}: {}", session.getId(), exerciseId);
                continue;
            }

            String primary = entity.getMuscleGroup();
            if (primary != null && !primary.isBlank()) {
                String canonical = primary.trim().toUpperCase(Locale.ROOT);
                if (CANONICAL_MUSCLE_GROUPS.contains(canonical) && !"FULL_BODY".equals(canonical)) {
                    muscles.add(canonical);
                }
            }

            String[] secondaries = entity.getSecondaryMuscles();
            if (secondaries != null) {
                for (String sec : secondaries) {
                    String mapped = mapSecondaryToCanonical(sec);
                    if (mapped != null) muscles.add(mapped);
                }
            }
        }

        return muscles;
    }

    /**
     * Map secondary_muscles vocabulary (e.g. "Rear delts", "Upper chest",
     * "Brachialis") to one of the 11 canonical muscle groups.
     *
     * The secondary_muscles array uses a richer vocabulary than
     * muscle_group. Here we collapse it to canonical groups for recovery
     * tracking purposes.
     *
     * Accessory/stabiliser muscles (forearms, hip flexors, rotator cuff)
     * are intentionally skipped — too peripheral to count as trained.
     */
    private String mapSecondaryToCanonical(String secondary) {
        if (secondary == null) return null;
        String n = secondary.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        return switch (n) {
            case "chest", "upper chest", "lower chest" -> "CHEST";
            case "back", "lats", "rhomboids", "traps", "upper traps",
                 "teres major", "erector spinae" -> "BACK";
            case "shoulders", "front delts", "rear delts",
                 "side delts", "medial delts" -> "SHOULDERS";
            case "biceps", "biceps short head",
                 "brachialis", "brachioradialis" -> "BICEPS";
            case "triceps", "anconeus" -> "TRICEPS";
            case "legs", "quads", "quadriceps", "rectus femoris" -> "LEGS";
            case "hamstrings" -> "HAMSTRINGS";
            case "glutes", "glute max", "glute medius" -> "GLUTES";
            case "calves", "soleus", "gastrocnemius" -> "CALVES";
            case "core", "abs", "abdominals", "obliques",
                 "transverse abdominis" -> "CORE";
            // Skip accessories
            case "forearms", "forearm flexors", "forearm extensors",
                 "hip flexors", "adductors", "abductors",
                 "external rotators", "serratus anterior",
                 "achilles tendon" -> null;
            default -> null;
        };
    }
}
