package com.fittribe.api.strengthscore;

import com.fittribe.api.entity.ExerciseStrengthTarget;
import com.fittribe.api.repository.ExerciseStrengthTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StrengthScoreService}.
 *
 * <p>{@link ExerciseStrengthTargetRepository} is mocked. The service's
 * strength score computation is tested with both weighted and bodyweight
 * exercises, fallback logic for null user attributes, and defensive bounds
 * checking.
 */
class StrengthScoreServiceTest {

    private StrengthScoreService service;
    private ExerciseStrengthTargetRepository fakeRepo;

    @BeforeEach
    void setUp() {
        fakeRepo = mock(ExerciseStrengthTargetRepository.class);
        service = new StrengthScoreService(fakeRepo);
    }

    // ── Epley 1RM formula ──────────────────────────────────────────────

    @Test
    void epley1RMComputesCorrectly() {
        // 100 kg × (1 + 5/30) = 100 × 1.1667 = 116.67
        double epley = service.computeEpley1RM(100.0, 5);
        assertEquals(116.67, epley, 0.01);
    }

    @Test
    void epley1RMDefensiveAgainstZeroReps() {
        double epley = service.computeEpley1RM(100.0, 0);
        assertEquals(0.0, epley);
    }

    @Test
    void epley1RMDefensiveAgainstNegativeReps() {
        double epley = service.computeEpley1RM(100.0, -5);
        assertEquals(0.0, epley);
    }

    // ── Weighted exercise scoring ──────────────────────────────────────

    @Test
    void weightedExerciseScore100WhenMatchesTarget() {
        // bench-press (MALE, BEGINNER): multiplier = 1.0
        // bodyweight = 75 kg → target 1RM = 1.0 × 75 = 75 kg
        // epley = 75 kg → score = (75 / 75) × 100 = 100
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, "MALE", "BEGINNER");
        assertEquals(100, score);
    }

    @Test
    void weightedExerciseScore50WhenHalfOfTarget() {
        // bench-press (MALE, BEGINNER): multiplier = 1.0
        // bodyweight = 75 kg → target = 75 kg
        // epley = 37.5 kg → score = (37.5 / 75) × 100 = 50
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 37.5, 75.0, "MALE", "BEGINNER");
        assertEquals(50, score);
    }

    @Test
    void weightedExerciseScoreCappedAt100() {
        // bench-press (MALE, BEGINNER): multiplier = 1.0
        // bodyweight = 75 kg → target = 75 kg
        // epley = 150 kg → raw score = 200, capped at 100
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 150.0, 75.0, "MALE", "BEGINNER");
        assertEquals(100, score);
    }

    // ── Bodyweight exercise scoring ────────────────────────────────────

    @Test
    void bodyweightExerciseScore100WhenMatchesTarget() {
        // push-ups (MALE, BEGINNER): repTarget = 10
        // bodyweight = 75 kg → targetRM = 75 × (1 + 10/30) = 100 kg
        // epley = 75 × (1 + 10/30) = 100 kg → score = 100
        ExerciseStrengthTarget target = buildTarget("push-ups", "MALE", "BEGINNER", null, 10);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("push-ups", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("push-ups", 100.0, 75.0, "MALE", "BEGINNER");
        assertEquals(100, score);
    }

    // ── Fallback logic ─────────────────────────────────────────────────

    @Test
    void nullGenderFallsBackToMale() {
        // null gender → MALE fallback
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, null, "BEGINNER");
        assertEquals(100, score);
    }

    @Test
    void blankGenderFallsBackToMale() {
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, "  ", "BEGINNER");
        assertEquals(100, score);
    }

    @Test
    void nullFitnessLevelFallsBackToBegineer() {
        // null fitnessLevel → BEGINNER fallback
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, "MALE", null);
        assertEquals(100, score);
    }

    // ── Missing exercise ──────────────────────────────────────────────

    @Test
    void unknownExerciseReturns0() {
        // Exercise not in seed data → no target found
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("flying-saucer-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.empty());

        int score = service.computeExerciseScore("flying-saucer-press", 100.0, 75.0, "MALE", "BEGINNER");
        assertEquals(0, score);
    }

    // ── Case normalization ────────────────────────────────────────────

    @Test
    void lowercaseGenderNormalizes() {
        // gender='male' (lowercase) should be normalized to 'MALE'
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, "male", "BEGINNER");
        assertEquals(100, score);
    }

    @Test
    void lowercaseFemaleGenderNormalizes() {
        // gender='female' (lowercase) should be normalized to 'FEMALE'
        ExerciseStrengthTarget target = buildTarget("bench-press", "FEMALE", "BEGINNER",
                new BigDecimal("0.50"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "FEMALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 37.5, 75.0, "female", "BEGINNER");
        assertEquals(100, score);
    }

    @Test
    void mixedCaseFitnessLevelNormalizes() {
        // fitnessLevel='BeGiNnEr' (mixed case) should be normalized to 'BEGINNER'
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, "MALE", "BeGiNnEr");
        assertEquals(100, score);
    }

    @Test
    void unknownGenderFallsBackToMaleWithRetry() {
        // gender='OTHER' (unknown) should normalize to 'OTHER', miss, then retry with MALE/BEGINNER
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "OTHER", "BEGINNER"))
                .thenReturn(Optional.empty());
        ExerciseStrengthTarget target = buildTarget("bench-press", "MALE", "BEGINNER",
                new BigDecimal("1.00"), null);
        when(fakeRepo.findByExerciseIdAndGenderAndFitnessLevel("bench-press", "MALE", "BEGINNER"))
                .thenReturn(Optional.of(target));

        int score = service.computeExerciseScore("bench-press", 75.0, 75.0, "OTHER", "BEGINNER");
        assertEquals(100, score);
    }

    // ── Helper ────────────────────────────────────────────────────────

    private static ExerciseStrengthTarget buildTarget(String exerciseId, String gender,
            String fitnessLevel, BigDecimal multiplier, Integer repTarget) {
        ExerciseStrengthTarget target = new ExerciseStrengthTarget();
        target.setExerciseId(exerciseId);
        target.setGender(gender);
        target.setFitnessLevel(fitnessLevel);
        target.setMultiplier(multiplier);
        target.setRepTarget(repTarget);
        target.setFormulaVersion(1);
        return target;
    }
}
