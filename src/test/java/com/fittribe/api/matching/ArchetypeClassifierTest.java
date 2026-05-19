package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.matching.ArchetypeClassifier.Result;
import org.junit.jupiter.api.Test;

import static com.fittribe.api.matching.MatchingAnswer.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ArchetypeClassifier#classify}.
 *
 * <p>Pure-logic classifier — no Spring context. Every signal total in the
 * test names below was computed by hand against
 * {@code ArchetypeClassifier.scoreOf} before the test was written; where a
 * candidate input didn't yield the intended archetype the <i>input</i> was
 * adjusted, never the classifier (per the Step 3 separation-of-concerns rule).
 */
class ArchetypeClassifierTest {

    @Test
    void classifies_returner_when_no_one_noticed_and_someone_cares() {
        // invisibility_wound = 3+1+1+2 = 7  (>= 4)
        Result r = ArchetypeClassifier.classify(
                NO_ONE_NOTICED, CELEBRATE_WINS, GO_THROUGH_PHASES, SOMEONE_CARES);
        assertEquals(Archetype.RETURNER, r.archetype());
    }

    @Test
    void classifies_returner_on_threshold_exactly_4() {
        // invisibility_wound = 3+0+1+0 = 4  (exactly at threshold)
        Result r = ArchetypeClassifier.classify(
                NO_ONE_NOTICED, PUSH_WHEN_SLACKING, GO_THROUGH_PHASES, FRIENDLY_COMPETITION);
        assertEquals(Archetype.RETURNER, r.archetype());
    }

    @Test
    void classifies_anchor_high_consistency_low_intensity() {
        // consistency = 2+0+3+1 = 6 (>=5), intensity = 0+0+1+0 = 1 (<3)
        Result r = ArchetypeClassifier.classify(
                LIFE_GOT_BUSY, CELEBRATE_WINS, SHOW_UP_ON_BAD_DAYS, SHARED_PROGRESS_TRACKING);
        assertEquals(Archetype.ANCHOR, r.archetype());
    }

    @Test
    void classifies_grinder_consistency_and_intensity_both_high() {
        // consistency = 2+0+3+0 = 5 (>=5), intensity = 0+2+1+3 = 6 (>=3)
        Result r = ArchetypeClassifier.classify(
                LIFE_GOT_BUSY, PUSH_WHEN_SLACKING, SHOW_UP_ON_BAD_DAYS, FRIENDLY_COMPETITION);
        assertEquals(Archetype.GRINDER, r.archetype());
    }

    @Test
    void classifies_social_butterfly_when_social_pull_high() {
        // invisibility_wound = 0+1+0+0 = 1 (<4), consistency = 2+0+1+1 = 4 (<5),
        // social_pull = 0+2+1+1 = 4 (>=4)
        Result r = ArchetypeClassifier.classify(
                LIFE_GOT_BUSY, CELEBRATE_WINS, NEED_NUDGING, SHARED_PROGRESS_TRACKING);
        assertEquals(Archetype.SOCIAL_BUTTERFLY, r.archetype());
    }

    @Test
    void classifies_striver_high_intensity_low_consistency() {
        // intensity = -1+2+0+3 = 4 (>=4), consistency = 0+0+1+0 = 1 (<5)
        Result r = ArchetypeClassifier.classify(
                LOST_MOTIVATION, PUSH_WHEN_SLACKING, NEED_NUDGING, FRIENDLY_COMPETITION);
        assertEquals(Archetype.STRIVER, r.archetype());
    }

    @Test
    void classifies_seeker_when_nothing_strong_enough() {
        // consistency = 2+0-1+1 = 2, all signals below threshold
        Result r = ArchetypeClassifier.classify(
                LIFE_GOT_BUSY, DOESNT_MATTER, STILL_FIGURING_OUT, SHARED_PROGRESS_TRACKING);
        assertEquals(Archetype.SEEKER, r.archetype());
    }

    @Test
    void returner_check_runs_before_social_butterfly_check() {
        // invisibility_wound = 3+1+0+2 = 6 (RETURNER threshold met)
        // social_pull        = 1+2+1+1 = 5 (SOCIAL_BUTTERFLY threshold also met)
        // RETURNER is checked first, so it wins — proves rule order matters.
        Result r = ArchetypeClassifier.classify(
                NO_ONE_NOTICED, CELEBRATE_WINS, NEED_NUDGING, SOMEONE_CARES);
        assertEquals(Archetype.RETURNER, r.archetype());
    }

    @Test
    void rejects_null_answer() {
        assertThrows(IllegalArgumentException.class, () ->
                ArchetypeClassifier.classify(null, BALANCED_MIX, NEED_NUDGING, SOCIAL_PRESSURE));
    }

    @Test
    void rejects_mismatched_question_slot() {
        // Q2 slot got a Q1 answer (LIFE_GOT_BUSY belongs to Q1_BREAK_REASON).
        assertThrows(IllegalArgumentException.class, () ->
                ArchetypeClassifier.classify(LIFE_GOT_BUSY, LIFE_GOT_BUSY, NEED_NUDGING, SOCIAL_PRESSURE));
    }

    @Test
    void per_question_scores_record_winning_signal_contributions() {
        // ANCHOR (winning signal = CONSISTENCY).
        // Q1 LIFE_GOT_BUSY -> consistency 2
        // Q2 CELEBRATE_WINS -> consistency 0
        // Q3 SHOW_UP_ON_BAD_DAYS -> consistency 3
        // Q4 SHARED_PROGRESS_TRACKING -> consistency 1
        Result r = ArchetypeClassifier.classify(
                LIFE_GOT_BUSY, CELEBRATE_WINS, SHOW_UP_ON_BAD_DAYS, SHARED_PROGRESS_TRACKING);
        assertEquals(Archetype.ANCHOR, r.archetype());
        assertEquals(2, r.scoreQ1());
        assertEquals(0, r.scoreQ2());
        assertEquals(3, r.scoreQ3());
        assertEquals(1, r.scoreQ4());
    }

    @Test
    void classifier_is_deterministic() {
        Result first = ArchetypeClassifier.classify(
                NO_ONE_NOTICED, CELEBRATE_WINS, GO_THROUGH_PHASES, SOMEONE_CARES);
        for (int i = 0; i < 100; i++) {
            Result again = ArchetypeClassifier.classify(
                    NO_ONE_NOTICED, CELEBRATE_WINS, GO_THROUGH_PHASES, SOMEONE_CARES);
            assertEquals(first, again);
        }
    }
}
