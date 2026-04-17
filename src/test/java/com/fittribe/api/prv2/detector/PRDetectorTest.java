package com.fittribe.api.prv2.detector;

import com.fittribe.api.entity.UserExerciseBests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for PRDetector.
 * No Spring, no database, no mocking.
 * Just instantiate PRDetector directly and call detect().
 */
@DisplayName("PRDetector — Pure function unit tests")
class PRDetectorTest {

    private PRDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PRDetector();
    }

    // ── FIRST_EVER tests ────────────────────────────────────────────────

    @Test
    @DisplayName("FIRST_EVER fires when currentBests is null")
    void firstEver_whenNullBests() {
        LoggedSet set = new LoggedSet(null, "bench-press", BigDecimal.valueOf(40.0), 10, null);
        PRResult result = detector.detect(set, null, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());
        assertEquals(3, result.suggestedCoins());
        assertTrue(result.signalsMet().containsKey("first_ever"));
    }

    @Test
    @DisplayName("FIRST_EVER fires for weighted exercise when totalSessionsWithExercise is 0")
    void firstEver_weightedExercise_zeroSessions() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setUserId(UUID.randomUUID());
        bests.setExerciseId("squat");
        bests.setExerciseType("WEIGHTED");
        bests.setTotalSessionsWithExercise(0);

        LoggedSet set = new LoggedSet(null, "squat", BigDecimal.valueOf(100.0), 5, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());
        assertEquals(3, result.suggestedCoins());
    }

    @Test
    @DisplayName("FIRST_EVER fires for bodyweight-unassisted exercise")
    void firstEver_bodyweightUnassisted() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setExerciseId("pushup");
        bests.setExerciseType("BODYWEIGHT_UNASSISTED");
        bests.setTotalSessionsWithExercise(0);

        LoggedSet set = new LoggedSet(null, "pushup", null, 15, null);
        PRResult result = detector.detect(set, bests, ExerciseType.BODYWEIGHT_UNASSISTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());
    }

    @Test
    @DisplayName("FIRST_EVER fires for timed exercise")
    void firstEver_timedExercise() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setExerciseId("plank");
        bests.setExerciseType("TIMED");
        bests.setTotalSessionsWithExercise(0);

        LoggedSet set = new LoggedSet(null, "plank", null, null, 60);
        PRResult result = detector.detect(set, bests, ExerciseType.TIMED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());
    }

    // ── WEIGHT_PR tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("WEIGHT_PR fires when weight > bestWtKg at exactly 3 reps")
    void weightPR_fireAt3Reps() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(45.0), 3, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.WEIGHT_PR, result.category());
        assertEquals(5, result.suggestedCoins());
    }

    @Test
    @DisplayName("WEIGHT_PR does NOT fire at 2 reps (falls to MAX_ATTEMPT)")
    void weightPR_notFireAt2Reps() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(45.0), 2, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.MAX_ATTEMPT, result.category());
    }

    // ── MAX_ATTEMPT tests ───────────────────────────────────────────────

    @Test
    @DisplayName("MAX_ATTEMPT fires at 1 rep with heavier weight")
    void maxAttempt_at1RepHeavierWeight() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(100.0));

        LoggedSet set = new LoggedSet(null, "squat", BigDecimal.valueOf(105.0), 1, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.MAX_ATTEMPT, result.category());
        assertTrue(result.signalsMet().containsKey("max_attempt"));
    }

    @Test
    @DisplayName("MAX_ATTEMPT fires at 2 reps with heavier weight")
    void maxAttempt_at2RepsHeavierWeight() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(100.0));

        LoggedSet set = new LoggedSet(null, "squat", BigDecimal.valueOf(110.0), 2, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.MAX_ATTEMPT, result.category());
    }

    @Test
    @DisplayName("MAX_ATTEMPT does NOT fire when weight unchanged")
    void maxAttempt_notFireWeightUnchanged() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(100.0));

        LoggedSet set = new LoggedSet(null, "squat", BigDecimal.valueOf(100.0), 1, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
    }

    // ── REP_PR tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("REP_PR fires when same weight, more reps")
    void repPR_sameWeightMoreReps() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40.0), 12, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.REP_PR, result.category());
        assertEquals(5, result.suggestedCoins());
        assertTrue(result.signalsMet().containsKey("rep"));
    }

    @Test
    @DisplayName("REP_PR does NOT fire when weight is lower")
    void repPR_notFireWeightLower() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(35.0), 15, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
    }

    // ── VOLUME_PR tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("VOLUME_PR fires when weight*reps exceeds best_set_volume_kg")
    void volumePR_exceedsVolume() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(400.0)); // 40 * 10

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(35.0), 12, null); // 35 * 12 = 420
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.VOLUME_PR, result.category());
        assertEquals(5, result.suggestedCoins());
        assertTrue(result.signalsMet().containsKey("volume"));
    }

    @Test
    @DisplayName("VOLUME_PR does NOT fire when weight PR already fired")
    void volumePR_notFireWhenWeightPRFired() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(400.0));

        // New weight at 3+ reps triggers WEIGHT_PR before VOLUME_PR
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(50.0), 3, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.WEIGHT_PR, result.category()); // Not VOLUME_PR
    }

    // ── No PR tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("No PR when all signals flat or lower")
    void noPR_allSignalsFlat() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(400.0));

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40.0), 8, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
        assertNull(result.category());
        assertEquals(0, result.suggestedCoins());
    }

    // ── TIMED exercise tests ────────────────────────────────────────────

    @Test
    @DisplayName("TIMED: first-ever hold fires FIRST_EVER")
    void timed_firstEverHold() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setExerciseId("plank");
        bests.setExerciseType("TIMED");
        bests.setTotalSessionsWithExercise(0);

        LoggedSet set = new LoggedSet(null, "plank", null, null, 45);
        PRResult result = detector.detect(set, bests, ExerciseType.TIMED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());
    }

    @Test
    @DisplayName("TIMED: hold improvement fires WEIGHT_PR category")
    void timed_holdImprovement() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestHoldSeconds(60);

        LoggedSet set = new LoggedSet(null, "plank", null, null, 75);
        PRResult result = detector.detect(set, bests, ExerciseType.TIMED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.WEIGHT_PR, result.category()); // Reuses WEIGHT_PR for UI
        assertTrue(result.signalsMet().containsKey("hold_time"));
        assertEquals(5, result.suggestedCoins());
    }

    // ── Edge case tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Null currentBests fires FIRST_EVER")
    void edgeCase_nullCurrentBests() {
        LoggedSet set = new LoggedSet(null, "deadlift", BigDecimal.valueOf(150.0), 5, null);
        PRResult result = detector.detect(set, null, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());
    }

    @Test
    @DisplayName("Zero weight with non-TIMED exercise returns no PR")
    void edgeCase_zeroWeightNonTimed() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(0), 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
    }

    @Test
    @DisplayName("Zero holdSeconds with TIMED exercise returns no PR")
    void edgeCase_zeroHoldSecondsTimed() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestHoldSeconds(60);

        LoggedSet set = new LoggedSet(null, "plank", null, null, 0);
        PRResult result = detector.detect(set, bests, ExerciseType.TIMED);

        assertFalse(result.isPR());
    }

    @Test
    @DisplayName("Exact tie (equal weight, equal reps) returns no PR")
    void edgeCase_exactTie() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40.0), 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
    }

    @Test
    @DisplayName("Null weight with weight-based exercise returns no PR")
    void edgeCase_nullWeightWeightBased() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));

        LoggedSet set = new LoggedSet(null, "bench", null, 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
    }

    @Test
    @DisplayName("Negative weight guard: returns no PR")
    void edgeCase_negativeWeight() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(-5.0), 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
    }

    @Test
    @DisplayName("Detector version is always v1.0")
    void edgeCase_detectorVersion() {
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40.0), 10, null);
        PRResult result = detector.detect(set, null, ExerciseType.WEIGHTED);

        assertEquals("v1.0", result.detectorVersion());
    }

    @Test
    @DisplayName("Waterfall: WEIGHT_PR wins over REP_PR")
    void waterfall_weightPRWinsOverRepPR() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);

        // Both WEIGHT_PR and REP_PR could fire, but WEIGHT_PR wins
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(45.0), 12, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertEquals(PrCategory.WEIGHT_PR, result.category());
    }

    @Test
    @DisplayName("Waterfall: REP_PR wins over VOLUME_PR")
    void waterfall_repPRWinsOverVolumePR() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40.0));
        bests.setRepsAtBestWt(10);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(400.0));

        // Rep PR fires first in waterfall
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40.0), 15, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertEquals(PrCategory.REP_PR, result.category());
    }

}
