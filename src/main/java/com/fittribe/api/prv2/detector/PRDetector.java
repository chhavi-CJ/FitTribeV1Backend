package com.fittribe.api.prv2.detector;

import com.fittribe.api.entity.UserExerciseBests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure function for PR detection.
 *
 * Given a logged set and the user's current exercise bests,
 * determines if a PR fired and which category.
 *
 * No database access. No Spring dependencies. Stateless.
 */
@Component
public class PRDetector {

    private static final Logger log = LoggerFactory.getLogger(PRDetector.class);

    public static final String DETECTOR_VERSION = "v1.0";

    public PRDetector() {
    }

    /**
     * Detect if the logged set constitutes a PR.
     *
     * @param set            the set that was logged
     * @param currentBests   current bests for this (user, exercise), or null for first-ever
     * @param exerciseType   the type of exercise
     * @return PRResult with detection outcome
     */
    public PRResult detect(LoggedSet set, UserExerciseBests currentBests, ExerciseType exerciseType) {
        // FIRST_EVER
        if (isFirstEver(currentBests)) {
            return prResult(PrCategory.FIRST_EVER, 3, signalsMet("first_ever", true),
                firstEverPayload(set, exerciseType));
        }

        // Only weight-based exercises can have WEIGHT_PR, MAX_ATTEMPT, REP_PR, VOLUME_PR
        if (isWeightBased(exerciseType)) {
            // WEIGHT_PR
            if (isWeightPR(set, currentBests)) {
                Map<String, Boolean> signals = signalsMet("weight", true);
                Map<String, Object> payload = weightPRPayload(set, currentBests);
                enrichWithVolumeIfApplicable(signals, payload, set, currentBests);
                return prResult(PrCategory.WEIGHT_PR, 5, signals, payload);
            }

            // MAX_ATTEMPT
            if (isMaxAttempt(set, currentBests)) {
                Map<String, Boolean> signals = signalsMet("max_attempt", true);
                Map<String, Object> payload = maxAttemptPayload(set, currentBests);
                enrichWithVolumeIfApplicable(signals, payload, set, currentBests);
                return prResult(PrCategory.MAX_ATTEMPT, 5, signals, payload);
            }

            // REP_PR
            if (isRepPR(set, currentBests, exerciseType)) {
                Map<String, Boolean> signals = signalsMet("rep", true);
                Map<String, Object> payload = repPRPayload(set, currentBests);
                enrichWithVolumeIfApplicable(signals, payload, set, currentBests);
                return prResult(PrCategory.REP_PR, 5, signals, payload);
            }

            // VOLUME_PR (sole winner — no secondary signals possible)
            if (isVolumePR(set, currentBests)) {
                return prResult(PrCategory.VOLUME_PR, 5, signalsMet("volume", true),
                    volumePRPayload(set, currentBests));
            }
        } else if (exerciseType == ExerciseType.TIMED) {
            // TIMED exercise PR (treated as WEIGHT_PR category for UI)
            if (isTimedPR(set, currentBests)) {
                return prResult(PrCategory.WEIGHT_PR, 5, signalsMet("hold_time", true),
                    timedPRPayload(set, currentBests));
            }
        }

        // No PR
        return noResult();
    }

    // ── Detection conditions ────────────────────────────────────────────

    private boolean isFirstEver(UserExerciseBests bests) {
        return bests == null || (bests.getTotalSessionsWithExercise() == null || bests.getTotalSessionsWithExercise() == 0);
    }

    private boolean isWeightBased(ExerciseType exerciseType) {
        return exerciseType == ExerciseType.WEIGHTED
            || exerciseType == ExerciseType.BODYWEIGHT_UNASSISTED
            || exerciseType == ExerciseType.BODYWEIGHT_ASSISTED
            || exerciseType == ExerciseType.BODYWEIGHT_WEIGHTED;
    }

    private boolean isWeightPR(LoggedSet set, UserExerciseBests bests) {
        // Guard: null or zero weight is corrupt data
        if (set.weightKg() == null || set.weightKg().signum() <= 0) {
            return false;
        }

        // Guard: null reps is corrupt data
        if (set.reps() == null || set.reps() <= 0) {
            return false;
        }

        // Weight PR: weight > best AND reps >= 3
        if (bests.getBestWtKg() == null) {
            return false;
        }

        return set.weightKg().compareTo(bests.getBestWtKg()) > 0 && set.reps() >= 3;
    }

    private boolean isMaxAttempt(LoggedSet set, UserExerciseBests bests) {
        // Guard: null or zero weight
        if (set.weightKg() == null || set.weightKg().signum() <= 0) {
            return false;
        }

        // Guard: null reps
        if (set.reps() == null) {
            return false;
        }

        // Max attempt: weight > best AND reps in {1, 2}
        if (bests.getBestWtKg() == null) {
            return false;
        }

        return set.weightKg().compareTo(bests.getBestWtKg()) > 0 && (set.reps() == 1 || set.reps() == 2);
    }

    private boolean isRepPR(LoggedSet set, UserExerciseBests bests, ExerciseType exerciseType) {
        // Bodyweight branch: compare reps against bestReps (no weight involved)
        // Applies to BODYWEIGHT_UNASSISTED and BODYWEIGHT_ASSISTED — both track reps only
        if (exerciseType == ExerciseType.BODYWEIGHT_UNASSISTED
                || exerciseType == ExerciseType.BODYWEIGHT_ASSISTED) {
            if (set.reps() == null) {
                return false;
            }
            int currentBestReps = bests.getBestReps() != null ? bests.getBestReps() : 0;
            return set.reps() > currentBestReps;
        }

        // Weighted branch: guard: null or zero weight
        if (set.weightKg() == null || set.weightKg().signum() <= 0) {
            return false;
        }

        // Guard: null reps
        if (set.reps() == null || set.reps() <= 0) {
            return false;
        }

        // Rep PR: same weight, more reps than ever at that weight
        // Check if weight matches best_wt_kg
        if (bests.getBestWtKg() == null) {
            return false;
        }

        if (set.weightKg().compareTo(bests.getBestWtKg()) != 0) {
            return false;
        }

        // Same weight — check if reps exceed reps_at_best_wt
        if (bests.getRepsAtBestWt() == null) {
            return false;
        }

        return set.reps() > bests.getRepsAtBestWt();
    }

    private boolean isVolumePR(LoggedSet set, UserExerciseBests bests) {
        // Guard: null or zero weight
        if (set.weightKg() == null || set.weightKg().signum() <= 0) {
            return false;
        }

        // Guard: null reps
        if (set.reps() == null || set.reps() <= 0) {
            return false;
        }

        // Volume PR: weight * reps > best_set_volume_kg
        if (bests.getBestSetVolumeKg() == null) {
            return false;
        }

        BigDecimal volume = set.weightKg().multiply(BigDecimal.valueOf(set.reps()));
        return volume.compareTo(bests.getBestSetVolumeKg()) > 0;
    }

    private boolean isTimedPR(LoggedSet set, UserExerciseBests bests) {
        // Guard: null or zero hold seconds
        if (set.holdSeconds() == null || set.holdSeconds() <= 0) {
            return false;
        }

        // Timed PR: holdSeconds > best_hold_seconds
        if (bests.getBestHoldSeconds() == null) {
            return false;
        }

        return set.holdSeconds() > bests.getBestHoldSeconds();
    }

    // ── Payload builders ────────────────────────────────────────────────

    private Map<String, Object> firstEverPayload(LoggedSet set, ExerciseType exerciseType) {
        Map<String, Object> payload = new HashMap<>();

        Map<String, Object> newBest = new HashMap<>();
        if (exerciseType == ExerciseType.TIMED) {
            newBest.put("hold_seconds", set.holdSeconds());
        } else {
            if (set.weightKg() != null) {
                newBest.put("weight_kg", set.weightKg());
            }
            if (set.reps() != null) {
                newBest.put("reps", set.reps());
            }
        }

        payload.put("new_best", newBest);
        return payload;
    }

    private Map<String, Object> weightPRPayload(LoggedSet set, UserExerciseBests bests) {
        Map<String, Object> payload = new HashMap<>();

        BigDecimal delta = set.weightKg().subtract(bests.getBestWtKg());
        payload.put("delta_kg", delta);

        Map<String, Object> previousBest = new HashMap<>();
        previousBest.put("weight_kg", bests.getBestWtKg());
        previousBest.put("reps", bests.getRepsAtBestWt());
        payload.put("previous_best", previousBest);

        Map<String, Object> newBest = new HashMap<>();
        newBest.put("weight_kg", set.weightKg());
        newBest.put("reps", set.reps());
        payload.put("new_best", newBest);

        return payload;
    }

    private Map<String, Object> maxAttemptPayload(LoggedSet set, UserExerciseBests bests) {
        Map<String, Object> payload = new HashMap<>();

        payload.put("weight_kg", set.weightKg());
        payload.put("reps", set.reps());
        payload.put("previous_best_weight_kg", bests.getBestWtKg());

        return payload;
    }

    private Map<String, Object> repPRPayload(LoggedSet set, UserExerciseBests bests) {
        Map<String, Object> payload = new HashMap<>();

        int delta = set.reps() - bests.getRepsAtBestWt();
        payload.put("delta_reps", delta);
        payload.put("weight_kg", set.weightKg());
        payload.put("previous_reps", bests.getRepsAtBestWt());
        payload.put("new_reps", set.reps());

        return payload;
    }

    private Map<String, Object> volumePRPayload(LoggedSet set, UserExerciseBests bests) {
        Map<String, Object> payload = new HashMap<>();

        BigDecimal newVolume = set.weightKg().multiply(BigDecimal.valueOf(set.reps()));
        BigDecimal delta = newVolume.subtract(bests.getBestSetVolumeKg());

        payload.put("delta_volume", delta);
        payload.put("previous_best_volume", bests.getBestSetVolumeKg());
        payload.put("new_volume", newVolume);

        return payload;
    }

    private Map<String, Object> timedPRPayload(LoggedSet set, UserExerciseBests bests) {
        Map<String, Object> payload = new HashMap<>();

        int delta = set.holdSeconds() - bests.getBestHoldSeconds();
        payload.put("delta_seconds", delta);
        payload.put("previous_best_seconds", bests.getBestHoldSeconds());
        payload.put("new_seconds", set.holdSeconds());

        return payload;
    }

    // ── Secondary signal enrichment ───────────────────────────────────────

    private void enrichWithVolumeIfApplicable(Map<String, Boolean> signals,
                                              Map<String, Object> payload,
                                              LoggedSet set,
                                              UserExerciseBests bests) {
        if (!isVolumePR(set, bests)) {
            return;
        }
        signals.put("volume", true);
        BigDecimal newVolume = set.weightKg().multiply(BigDecimal.valueOf(set.reps()));
        BigDecimal deltaVolume = newVolume.subtract(bests.getBestSetVolumeKg());
        payload.put("delta_volume", deltaVolume);
        payload.put("previous_best_volume", bests.getBestSetVolumeKg());
        payload.put("new_volume", newVolume);
    }

    // ── Result builders ─────────────────────────────────────────────────

    private PRResult prResult(PrCategory category, int coins, Map<String, Boolean> signals,
                              Map<String, Object> payload) {
        log.debug("PR detected: category={} coins={}", category, coins);
        return new PRResult(true, category, signals, payload, coins, DETECTOR_VERSION);
    }

    private PRResult noResult() {
        return new PRResult(false, null, Map.of(), Map.of(), 0, DETECTOR_VERSION);
    }

    private Map<String, Boolean> signalsMet(String signalKey, boolean value) {
        Map<String, Boolean> signals = new HashMap<>();
        signals.put(signalKey, value);
        return signals;
    }
}
