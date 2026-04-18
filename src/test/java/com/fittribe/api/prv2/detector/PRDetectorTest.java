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

    // ── PR 1.5: Multi-signal enrichment tests ───────────────────────────

    @Test
    @DisplayName("Multi-signal: FIRST_EVER has only first_ever signal, no volume co-signal")
    void multiSignal_firstEverAlone() {
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40.0), 10, null);
        PRResult result = detector.detect(set, null, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.FIRST_EVER, result.category());

        // signalsMet: only first_ever
        assertEquals(1, result.signalsMet().size());
        assertTrue(result.signalsMet().get("first_ever"));
        assertNull(result.signalsMet().get("volume"));
        assertNull(result.signalsMet().get("weight"));

        // valuePayload: only new_best, no volume fields
        assertNotNull(result.valuePayload().get("new_best"));
        assertNull(result.valuePayload().get("delta_volume"));
        assertNull(result.valuePayload().get("previous_best_volume"));
        assertNull(result.valuePayload().get("new_volume"));
    }

    @Test
    @DisplayName("Multi-signal: WEIGHT_PR + simultaneous volume improvement")
    void multiSignal_weightPrPlusVolume() {
        // bests: 45kg x 8, volume best 360 (45*8)
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(45));
        bests.setRepsAtBestWt(8);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(360)); // 45 * 8

        // set: 50kg x 10 → weight PR (50>45, reps≥3) AND volume PR (500>360)
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(50), 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.WEIGHT_PR, result.category());
        assertEquals(5, result.suggestedCoins());

        // signalsMet: both weight and volume
        assertTrue(result.signalsMet().get("weight"));
        assertTrue(result.signalsMet().get("volume"));
        assertEquals(2, result.signalsMet().size());

        // valuePayload: weight PR fields present
        assertNotNull(result.valuePayload().get("delta_kg"));
        assertNotNull(result.valuePayload().get("previous_best"));
        assertNotNull(result.valuePayload().get("new_best"));

        // valuePayload: volume enrichment fields present with correct values
        assertEquals(new BigDecimal("500"), result.valuePayload().get("new_volume"));
        assertEquals(BigDecimal.valueOf(360), result.valuePayload().get("previous_best_volume"));
        assertEquals(new BigDecimal("140"), result.valuePayload().get("delta_volume"));
    }

    @Test
    @DisplayName("Multi-signal: REP_PR + simultaneous volume improvement")
    void multiSignal_repPrPlusVolume() {
        // bests: 45kg x 8, volume best 360
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(45));
        bests.setRepsAtBestWt(8);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(360)); // 45 * 8

        // set: 45kg x 10 → REP_PR (same weight, 10>8) AND volume PR (450>360)
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(45), 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.REP_PR, result.category());

        // signalsMet: both rep and volume
        assertTrue(result.signalsMet().get("rep"));
        assertTrue(result.signalsMet().get("volume"));
        assertEquals(2, result.signalsMet().size());

        // valuePayload: rep PR fields present
        assertEquals(2, result.valuePayload().get("delta_reps")); // 10 - 8
        assertEquals(BigDecimal.valueOf(45), result.valuePayload().get("weight_kg"));
        assertEquals(8, result.valuePayload().get("previous_reps"));
        assertEquals(10, result.valuePayload().get("new_reps"));

        // valuePayload: volume enrichment fields present
        assertEquals(new BigDecimal("450"), result.valuePayload().get("new_volume"));
        assertEquals(BigDecimal.valueOf(360), result.valuePayload().get("previous_best_volume"));
        assertEquals(new BigDecimal("90"), result.valuePayload().get("delta_volume"));
    }

    @Test
    @DisplayName("Multi-signal: MAX_ATTEMPT + simultaneous volume improvement")
    void multiSignal_maxAttemptPlusVolume() {
        // bests: 45kg x 8, volume best 50 (low enough that 50x2=100 beats it)
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(45));
        bests.setRepsAtBestWt(8);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(50));

        // set: 50kg x 2 → MAX_ATTEMPT (50>45, reps=2) AND volume PR (100>50)
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(50), 2, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.MAX_ATTEMPT, result.category());

        // signalsMet: both max_attempt and volume
        assertTrue(result.signalsMet().get("max_attempt"));
        assertTrue(result.signalsMet().get("volume"));
        assertEquals(2, result.signalsMet().size());

        // valuePayload: max attempt fields present
        assertEquals(BigDecimal.valueOf(50), result.valuePayload().get("weight_kg"));
        assertEquals(2, result.valuePayload().get("reps"));
        assertEquals(BigDecimal.valueOf(45), result.valuePayload().get("previous_best_weight_kg"));

        // valuePayload: volume enrichment fields present
        assertEquals(new BigDecimal("100"), result.valuePayload().get("new_volume"));
        assertEquals(BigDecimal.valueOf(50), result.valuePayload().get("previous_best_volume"));
        assertEquals(new BigDecimal("50"), result.valuePayload().get("delta_volume"));
    }

    @Test
    @DisplayName("Multi-signal: WEIGHT_PR without volume (volume best too high)")
    void multiSignal_weightPrWithoutVolume() {
        // bests: 45kg x 8, volume best 600 (high enough that 50x10=500 doesn't beat it)
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(45));
        bests.setRepsAtBestWt(8);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(600));

        // set: 50kg x 10 → WEIGHT_PR (50>45) but NOT volume PR (500 < 600)
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(50), 10, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.WEIGHT_PR, result.category());

        // signalsMet: only weight, no volume
        assertTrue(result.signalsMet().get("weight"));
        assertNull(result.signalsMet().get("volume"));
        assertEquals(1, result.signalsMet().size());

        // valuePayload: weight fields present, no volume fields
        assertNotNull(result.valuePayload().get("delta_kg"));
        assertNull(result.valuePayload().get("delta_volume"));
        assertNull(result.valuePayload().get("previous_best_volume"));
        assertNull(result.valuePayload().get("new_volume"));
    }

    @Test
    @DisplayName("Multi-signal: Non-PR set has empty signals and payload")
    void multiSignal_noPR() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(50));
        bests.setRepsAtBestWt(12);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(600));

        // set: 40kg x 8 → nothing beats anything
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(40), 8, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertFalse(result.isPR());
        assertNull(result.category());
        assertEquals(0, result.suggestedCoins());
        assertTrue(result.signalsMet().isEmpty());
        assertTrue(result.valuePayload().isEmpty());
    }

    @Test
    @DisplayName("Multi-signal: VOLUME_PR as sole winner has only volume signal")
    void multiSignal_volumePrSoleWinner() {
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(40));
        bests.setRepsAtBestWt(10);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(400)); // 40 * 10

        // set: 35kg x 12 = 420 → volume PR only (weight < best, different weight so no rep PR)
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(35), 12, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.VOLUME_PR, result.category());

        // signalsMet: only volume
        assertTrue(result.signalsMet().get("volume"));
        assertEquals(1, result.signalsMet().size());

        // valuePayload: volume fields present, no weight/rep/max fields
        assertNotNull(result.valuePayload().get("delta_volume"));
        assertNotNull(result.valuePayload().get("previous_best_volume"));
        assertNotNull(result.valuePayload().get("new_volume"));
        assertNull(result.valuePayload().get("delta_kg"));
        assertNull(result.valuePayload().get("delta_reps"));
    }

    @Test
    @DisplayName("Multi-signal: MAX_ATTEMPT without volume (volume best too high)")
    void multiSignal_maxAttemptWithoutVolume() {
        // bests: 45kg x 8, volume best 400 (50x1=50 doesn't beat it)
        UserExerciseBests bests = new UserExerciseBests();
        bests.setTotalSessionsWithExercise(5);
        bests.setBestWtKg(BigDecimal.valueOf(45));
        bests.setRepsAtBestWt(8);
        bests.setBestSetVolumeKg(BigDecimal.valueOf(400));

        // set: 50kg x 1 → MAX_ATTEMPT (50>45, reps=1) but NOT volume (50 < 400)
        LoggedSet set = new LoggedSet(null, "bench", BigDecimal.valueOf(50), 1, null);
        PRResult result = detector.detect(set, bests, ExerciseType.WEIGHTED);

        assertTrue(result.isPR());
        assertEquals(PrCategory.MAX_ATTEMPT, result.category());

        // signalsMet: only max_attempt, no volume
        assertTrue(result.signalsMet().get("max_attempt"));
        assertNull(result.signalsMet().get("volume"));
        assertEquals(1, result.signalsMet().size());

        // valuePayload: max attempt fields present, no volume fields
        assertEquals(BigDecimal.valueOf(50), result.valuePayload().get("weight_kg"));
        assertEquals(1, result.valuePayload().get("reps"));
        assertEquals(BigDecimal.valueOf(45), result.valuePayload().get("previous_best_weight_kg"));
        assertNull(result.valuePayload().get("delta_volume"));
        assertNull(result.valuePayload().get("previous_best_volume"));
        assertNull(result.valuePayload().get("new_volume"));
    }

}
