package com.fittribe.api.strengthscore;

import com.fittribe.api.entity.ExerciseStrengthTarget;
import com.fittribe.api.repository.ExerciseStrengthTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Service to compute strength scores from logged exercises.
 *
 * <h3>1-rep max estimation (Epley formula)</h3>
 * {@code 1RM = weight × (1 + reps / 30.0)}
 *
 * <p>For bodyweight exercises, the caller passes the user's
 * {@code bodyweightKg} as the {@code weightKg} parameter.
 *
 * <h3>Score normalization</h3>
 * {@code score = (epley1RM / targetRM) × 100}, capped at 100.
 *
 * <p>A score of 100 means the user hit the target exactly. A score
 * above 100 is capped — the UI shows "+100", not "150". A score of 0
 * means the exercise was not found in the reference table (skip from
 * scoring).
 *
 * <h3>Fallback logic</h3>
 * When a user's profile is incomplete (null gender or fitness level),
 * the service silently falls back to conservative defaults (MALE for
 * gender, BEGINNER for level) and logs at DEBUG level. This prevents
 * score computation from crashing on incomplete profiles.
 */
@Service
public class StrengthScoreService {

    private static final Logger log = LoggerFactory.getLogger(StrengthScoreService.class);

    private static final String GENDER_FALLBACK         = "MALE";
    private static final String FITNESS_LEVEL_FALLBACK  = "BEGINNER";

    private final ExerciseStrengthTargetRepository targetRepo;

    public StrengthScoreService(ExerciseStrengthTargetRepository targetRepo) {
        this.targetRepo = targetRepo;
    }

    /**
     * Estimate 1-rep max using the Epley formula.
     *
     * @param weightKg weight lifted (or bodyweight for bodyweight exercises)
     * @param reps number of reps performed
     * @return estimated 1RM in kg, or 0.0 if reps ≤ 0 (defensive)
     */
    public double computeEpley1RM(double weightKg, int reps) {
        if (reps <= 0) {
            return 0.0;
        }
        return weightKg * (1.0 + reps / 30.0);
    }

    /**
     * Compute normalised strength score (0–100) for one exercise.
     *
     * <p>Looks up the target 1RM for the exercise / gender / fitness-level
     * combination, then compares the user's estimated 1RM to the target.
     * Returns 0 if the exercise has no seed row in the reference table
     * (skip from scoring).
     *
     * @param exerciseId the exercise ID (e.g., "bench-press")
     * @param epley1RM the user's estimated 1RM
     * @param bodyweightKg the user's current weight in kg
     * @param gender the user's gender (may be null)
     * @param fitnessLevel the user's fitness level (may be null)
     * @return score 0–100, capped at 100
     */
    public int computeExerciseScore(
            String exerciseId,
            double epley1RM,
            double bodyweightKg,
            String gender,
            String fitnessLevel) {

        // ── Resolve and normalize gender/fitnessLevel ──────────────────
        String resolvedGender = normalizeWithFallback(gender, GENDER_FALLBACK, "gender");
        String resolvedLevel   = normalizeWithFallback(fitnessLevel, FITNESS_LEVEL_FALLBACK, "fitnessLevel");

        // ── Attempt lookup with resolved (normalized) values ───────────
        Optional<ExerciseStrengthTarget> targetOpt = targetRepo
                .findByExerciseIdAndGenderAndFitnessLevel(exerciseId, resolvedGender, resolvedLevel);

        // ── If miss AND resolved values differ from fallbacks, retry with fallbacks
        if (targetOpt.isEmpty() &&
            (!resolvedGender.equals(GENDER_FALLBACK) || !resolvedLevel.equals(FITNESS_LEVEL_FALLBACK))) {
            log.debug("No strength target found for exerciseId={}, gender={}, fitnessLevel={}; " +
                    "retrying with fallbacks MALE/BEGINNER", exerciseId, resolvedGender, resolvedLevel);
            targetOpt = targetRepo.findByExerciseIdAndGenderAndFitnessLevel(
                    exerciseId, GENDER_FALLBACK, FITNESS_LEVEL_FALLBACK);
        }

        if (targetOpt.isEmpty()) {
            log.debug("No strength target found for exerciseId={} even with fallbacks; " +
                    "skipping from score", exerciseId);
            return 0;
        }

        ExerciseStrengthTarget target = targetOpt.get();

        // ── Compute target 1RM ─────────────────────────────────────────
        double targetRM;
        if (target.getMultiplier() != null) {
            // Weighted exercise: targetRM = multiplier × bodyweightKg
            targetRM = target.getMultiplier().doubleValue() * bodyweightKg;
        } else if (target.getRepTarget() != null) {
            // Bodyweight exercise: targetRM = bodyweightKg × (1 + repTarget / 30.0)
            targetRM = bodyweightKg * (1.0 + target.getRepTarget() / 30.0);
        } else {
            // Neither multiplier nor repTarget is set — data error
            log.warn("Exercise {} has neither multiplier nor repTarget set; " +
                    "skipping from score", exerciseId);
            return 0;
        }

        if (targetRM <= 0) {
            log.warn("Target 1RM is <= 0 for exerciseId={}, bodyweightKg={}; " +
                    "skipping from score", exerciseId, bodyweightKg);
            return 0;
        }

        // ── Compute and cap score ──────────────────────────────────────
        double rawScore = (epley1RM / targetRM) * 100.0;
        int score = (int) Math.round(rawScore);
        score = Math.max(0, score);           // Defensive: clamp bottom
        score = Math.min(100, score);         // Cap at 100

        return score;
    }

    /**
     * Normalize a potentially-null value to uppercase and provide fallback.
     *
     * <p>If the value is null or blank, returns the fallback (which is already
     * uppercase). Otherwise, normalizes to uppercase using Locale.ROOT to ensure
     * consistent matching against seed data (e.g., 'male' → 'MALE').
     */
    private String normalizeWithFallback(String value, String fallback, String fieldName) {
        if (value == null || value.isBlank()) {
            log.debug("{} is null/blank; falling back to {}", fieldName, fallback);
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
