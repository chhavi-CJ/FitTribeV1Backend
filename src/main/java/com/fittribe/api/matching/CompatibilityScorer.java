package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;

/**
 * Pure-logic compatibility scorer for Conscious Matching (PRD §6).
 *
 * Given two {@link MatchingProfile}s, returns a compatibility score
 * (higher = better fit). Used by {@link GroupBuilder} to rank candidates
 * against a seed user. Symmetric: score(a, b) == score(b, a).
 *
 * <p><b>Scoring model (locked):</b>
 * <ol>
 *   <li><b>Archetype affinity</b> — table-driven, ranging from -3
 *       (two-fragile collision) to +5 (ANCHOR + RETURNER, the strongest
 *       pairing per PRD).</li>
 *   <li><b>Intensity compatibility</b> — small adjustment based on
 *       whether the two profiles have similar intensity scores.
 *       Approximated using {@code scoreQ4} (the motivation question is
 *       the most intensity-loaded answer).</li>
 * </ol>
 *
 * <p>This class does NOT enforce hard rules (gender, all-fragile groups,
 * RETURNER stabilizer) — those live in {@link HardRuleChecker}.
 *
 * <p>This class does NOT apply scarcity penalties — those depend on the
 * full pool and live in {@link GroupBuilder}.
 */
public final class CompatibilityScorer {

    private CompatibilityScorer() {}

    /**
     * Compute compatibility between two profiles. Always symmetric.
     *
     * @throws IllegalArgumentException if either profile is null.
     */
    public static int score(MatchingProfile a, MatchingProfile b) {
        if (a == null || b == null) throw new IllegalArgumentException("profile is null");
        if (a.getUserId() != null && a.getUserId().equals(b.getUserId())) {
            throw new IllegalArgumentException("Cannot score a profile against itself");
        }
        int affinity = archetypeAffinity(a.getArchetype(), b.getArchetype());
        int intensity = intensityCompat(a.getScoreQ4(), b.getScoreQ4());
        return affinity + intensity;
    }

    /**
     * The archetype affinity table from PRD §6. Symmetric — order of
     * inputs doesn't matter. Package-private for unit testing.
     */
    static int archetypeAffinity(Archetype x, Archetype y) {
        // Order-independent pair matching. Each rule expresses an unordered
        // pair {x, y} regardless of input order.

        // Strongest pairings.
        if (pair(x, y, Archetype.ANCHOR,  Archetype.RETURNER)) return +5;
        if (pair(x, y, Archetype.GRINDER, Archetype.STRIVER))  return +4;
        if (pair(x, y, Archetype.GRINDER, Archetype.RETURNER)) return +4;

        // Universal stabilizers: ANCHOR or GRINDER paired with non-stabilizer.
        if (pair(x, y, Archetype.ANCHOR,  Archetype.STRIVER))          return +2;
        if (pair(x, y, Archetype.ANCHOR,  Archetype.SOCIAL_BUTTERFLY)) return +3;
        if (pair(x, y, Archetype.ANCHOR,  Archetype.GRINDER))          return +3;
        if (pair(x, y, Archetype.ANCHOR,  Archetype.SEEKER))           return +3;
        if (pair(x, y, Archetype.GRINDER, Archetype.SOCIAL_BUTTERFLY)) return +3;
        if (pair(x, y, Archetype.GRINDER, Archetype.SEEKER))           return +3;

        // Same-stabilizer pairs.
        if (x == Archetype.ANCHOR  && y == Archetype.ANCHOR)  return +3;
        if (x == Archetype.GRINDER && y == Archetype.GRINDER) return +3;

        // SEEKER + SEEKER and SOCIAL_BUTTERFLY + SOCIAL_BUTTERFLY.
        if (x == Archetype.SEEKER && y == Archetype.SEEKER) return +2;
        if (x == Archetype.SOCIAL_BUTTERFLY && y == Archetype.SOCIAL_BUTTERFLY) return +2;

        // Same-fragile-type collisions (SB+SB carved out above).
        if (x == Archetype.STRIVER  && y == Archetype.STRIVER)  return -3;
        if (x == Archetype.RETURNER && y == Archetype.RETURNER) return -3;

        // Cross-fragile pairs (different wounds, no stabilizer).
        if (pair(x, y, Archetype.STRIVER,  Archetype.RETURNER))          return -2;
        if (pair(x, y, Archetype.STRIVER,  Archetype.SOCIAL_BUTTERFLY))  return -2;
        if (pair(x, y, Archetype.RETURNER, Archetype.SOCIAL_BUTTERFLY))  return -2;

        // SEEKER + fragile — neutral.
        if (pair(x, y, Archetype.SEEKER, Archetype.STRIVER))          return 0;
        if (pair(x, y, Archetype.SEEKER, Archetype.RETURNER))         return 0;
        if (pair(x, y, Archetype.SEEKER, Archetype.SOCIAL_BUTTERFLY)) return 0;

        // Fallthrough — neutral. Reaching here means a same-archetype
        // pairing not enumerated above (shouldn't happen given the cases
        // above cover all six same-archetype pairs).
        return 0;
    }

    /** Returns true if the unordered pair {x, y} equals the unordered pair {a, b}. */
    private static boolean pair(Archetype x, Archetype y, Archetype a, Archetype b) {
        return (x == a && y == b) || (x == b && y == a);
    }

    /**
     * Intensity compatibility, approximated by Q4-score similarity.
     * Q4 (motivation) is the most intensity-loaded question — the
     * "friendly competition" answer alone contributes +3 to the
     * intensity signal in the classifier.
     *
     * <p>The Q4 score stored on a profile is the contribution of Q4 to
     * the user's <i>winning signal</i> — which is informational not
     * dimensional, so we use absolute difference as a rough proxy.
     */
    static int intensityCompat(int q4ScoreA, int q4ScoreB) {
        int diff = Math.abs(q4ScoreA - q4ScoreB);
        if (diff <= 1) return +1;
        if (diff >= 3) return -1;
        return 0;
    }
}
