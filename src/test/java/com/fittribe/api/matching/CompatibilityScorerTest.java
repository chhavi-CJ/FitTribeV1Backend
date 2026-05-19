package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link CompatibilityScorer#score}.
 *
 * <p>Pure-logic scorer — no Spring context, no DB. Expected totals are
 * computed by hand against the locked scoring model and hardcoded; a
 * mismatch is treated as a scorer bug to flag, never a reason to edit
 * the scorer or weaken the test (per the Step 4b.i.A discipline rule).
 */
class CompatibilityScorerTest {

    private static MatchingProfile profile(Archetype archetype, int scoreQ4) {
        MatchingProfile p = new MatchingProfile();
        p.setUserId(UUID.randomUUID());
        p.setArchetype(archetype);
        p.setScoreQ4(scoreQ4);
        // Leave other fields null/0 — scorer only reads archetype + scoreQ4.
        return p;
    }

    // ── Pairings ─────────────────────────────────────────────────────

    @Test
    void anchor_plus_returner_scores_highest() {
        // archetype +5, intensity (q4 diff 0) +1 = 6
        assertEquals(6, CompatibilityScorer.score(
                profile(Archetype.ANCHOR, 0), profile(Archetype.RETURNER, 0)));
    }

    @Test
    void grinder_plus_striver_strong_pairing() {
        // archetype +4, intensity +1 = 5
        assertEquals(5, CompatibilityScorer.score(
                profile(Archetype.GRINDER, 0), profile(Archetype.STRIVER, 0)));
    }

    @Test
    void grinder_plus_returner_strong_pairing() {
        // archetype +4, intensity +1 = 5
        assertEquals(5, CompatibilityScorer.score(
                profile(Archetype.GRINDER, 0), profile(Archetype.RETURNER, 0)));
    }

    @Test
    void anchor_plus_anchor_two_stabilizers() {
        // archetype +3, intensity +1 = 4
        assertEquals(4, CompatibilityScorer.score(
                profile(Archetype.ANCHOR, 0), profile(Archetype.ANCHOR, 0)));
    }

    @Test
    void striver_plus_striver_fragile_collision() {
        // archetype -3, intensity +1 = -2
        assertEquals(-2, CompatibilityScorer.score(
                profile(Archetype.STRIVER, 0), profile(Archetype.STRIVER, 0)));
    }

    @Test
    void returner_plus_returner_fragile_collision() {
        // archetype -3, intensity +1 = -2
        assertEquals(-2, CompatibilityScorer.score(
                profile(Archetype.RETURNER, 0), profile(Archetype.RETURNER, 0)));
    }

    @Test
    void social_butterfly_plus_social_butterfly_is_positive_not_negative() {
        // PRD carve-out: group IS the point. archetype +2, intensity +1 = 3
        assertEquals(3, CompatibilityScorer.score(
                profile(Archetype.SOCIAL_BUTTERFLY, 0), profile(Archetype.SOCIAL_BUTTERFLY, 0)));
    }

    @Test
    void striver_plus_returner_cross_fragile_negative() {
        // archetype -2, intensity +1 = -1
        assertEquals(-1, CompatibilityScorer.score(
                profile(Archetype.STRIVER, 0), profile(Archetype.RETURNER, 0)));
    }

    @Test
    void seeker_plus_anchor_seeker_gets_stable_anchor() {
        // archetype +3, intensity +1 = 4
        assertEquals(4, CompatibilityScorer.score(
                profile(Archetype.SEEKER, 0), profile(Archetype.ANCHOR, 0)));
    }

    @Test
    void seeker_plus_striver_neutral() {
        // archetype 0, intensity +1 = 1
        assertEquals(1, CompatibilityScorer.score(
                profile(Archetype.SEEKER, 0), profile(Archetype.STRIVER, 0)));
    }

    // ── Symmetry ─────────────────────────────────────────────────────

    @Test
    void score_is_symmetric() {
        for (Archetype a : Archetype.values()) {
            for (Archetype b : Archetype.values()) {
                if (a == b) continue;
                int ab = CompatibilityScorer.score(profile(a, 0), profile(b, 0));
                int ba = CompatibilityScorer.score(profile(b, 0), profile(a, 0));
                assertEquals(ab, ba, "asymmetric for " + a + " vs " + b);
            }
        }
    }

    // ── Intensity ────────────────────────────────────────────────────

    @Test
    void high_intensity_diff_penalizes() {
        // ANCHOR+ANCHOR archetype +3, q4 diff 6 (>=3) -> intensity -1 = 2
        assertEquals(2, CompatibilityScorer.score(
                profile(Archetype.ANCHOR, 3), profile(Archetype.ANCHOR, -3)));
    }

    @Test
    void moderate_intensity_diff_neutral() {
        // ANCHOR+ANCHOR archetype +3, q4 diff 2 -> intensity 0 = 3
        assertEquals(3, CompatibilityScorer.score(
                profile(Archetype.ANCHOR, 2), profile(Archetype.ANCHOR, 0)));
    }

    // ── Validation ───────────────────────────────────────────────────

    @Test
    void rejects_null_profile() {
        assertThrows(IllegalArgumentException.class,
                () -> CompatibilityScorer.score(null, profile(Archetype.ANCHOR, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> CompatibilityScorer.score(profile(Archetype.ANCHOR, 0), null));
    }

    @Test
    void rejects_self_scoring() {
        MatchingProfile p = profile(Archetype.ANCHOR, 0);
        MatchingProfile sameUser = profile(Archetype.RETURNER, 0);
        sameUser.setUserId(p.getUserId());
        assertThrows(IllegalArgumentException.class,
                () -> CompatibilityScorer.score(p, sameUser));
    }
}
