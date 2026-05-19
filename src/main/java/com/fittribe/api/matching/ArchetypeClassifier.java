package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure-logic archetype classifier for Conscious Matching (PRD §3–§4).
 *
 * Four answers in, one archetype out, deterministic, always returns
 * exactly one value. No DB, no Spring beans, no I/O — by design.
 *
 * <p><b>Scoring model (locked):</b> each answer contributes to one or
 * more of four signals — consistency, social_pull, invisibility_wound,
 * intensity. Totals are summed across all four answers, then the
 * archetype is determined by a first-match-wins rule order that
 * prioritizes RETURNER (the ICP this feature most needs to identify per
 * PRD §1).
 *
 * <p><b>Per-question scores</b> returned alongside the archetype are the
 * sum of that question's contributions to the <i>winning</i> signal — so
 * matching_profile.score_qN records how much each answer pushed toward
 * the final classification. This is for debuggability, not used by the
 * matching engine.
 */
public final class ArchetypeClassifier {

    private ArchetypeClassifier() {}

    /** Internal scoring signals. Package-private for tests. */
    enum Signal { CONSISTENCY, SOCIAL_PULL, INVISIBILITY_WOUND, INTENSITY }

    /**
     * Result of classification — the archetype, the winning-signal totals,
     * and per-question contributions to the winning signal.
     */
    public record Result(
            Archetype archetype,
            int scoreQ1,
            int scoreQ2,
            int scoreQ3,
            int scoreQ4
    ) {}

    /**
     * Classify a user given their four answers.
     *
     * @throws IllegalArgumentException if any answer is null, or if any
     *         answer's tagged question does not match its slot.
     */
    public static Result classify(
            MatchingAnswer a1,
            MatchingAnswer a2,
            MatchingAnswer a3,
            MatchingAnswer a4
    ) {
        requireQuestion(a1, MatchingQuestion.Q1_BREAK_REASON);
        requireQuestion(a2, MatchingQuestion.Q2_GROUP_ENERGY);
        requireQuestion(a3, MatchingQuestion.Q3_CONSISTENCY);
        requireQuestion(a4, MatchingQuestion.Q4_MOTIVATION);

        Map<Signal, Integer> q1Score = scoreOf(a1);
        Map<Signal, Integer> q2Score = scoreOf(a2);
        Map<Signal, Integer> q3Score = scoreOf(a3);
        Map<Signal, Integer> q4Score = scoreOf(a4);

        int consistency        = sumSignal(Signal.CONSISTENCY,        q1Score, q2Score, q3Score, q4Score);
        int socialPull         = sumSignal(Signal.SOCIAL_PULL,        q1Score, q2Score, q3Score, q4Score);
        int invisibilityWound  = sumSignal(Signal.INVISIBILITY_WOUND, q1Score, q2Score, q3Score, q4Score);
        int intensity          = sumSignal(Signal.INTENSITY,          q1Score, q2Score, q3Score, q4Score);

        // First-match-wins rule order — see PRD §4 and Step 3 design notes.
        // RETURNER is checked first because identifying this ICP is the
        // PRD's stated primary purpose.
        Archetype archetype;
        Signal winningSignal;
        if (invisibilityWound >= 4) {
            archetype = Archetype.RETURNER;
            winningSignal = Signal.INVISIBILITY_WOUND;
        } else if (consistency >= 5 && intensity >= 3) {
            archetype = Archetype.GRINDER;
            // GRINDER is consistency+intensity — record contributions to
            // consistency (the stronger of the two thresholds) for
            // debuggability.
            winningSignal = Signal.CONSISTENCY;
        } else if (consistency >= 5) {
            archetype = Archetype.ANCHOR;
            winningSignal = Signal.CONSISTENCY;
        } else if (socialPull >= 4) {
            archetype = Archetype.SOCIAL_BUTTERFLY;
            winningSignal = Signal.SOCIAL_PULL;
        } else if (intensity >= 4) {
            archetype = Archetype.STRIVER;
            winningSignal = Signal.INTENSITY;
        } else {
            archetype = Archetype.SEEKER;
            // SEEKER is the fallback — record consistency contributions
            // as a placeholder; the value is informational only.
            winningSignal = Signal.CONSISTENCY;
        }

        return new Result(
                archetype,
                q1Score.getOrDefault(winningSignal, 0),
                q2Score.getOrDefault(winningSignal, 0),
                q3Score.getOrDefault(winningSignal, 0),
                q4Score.getOrDefault(winningSignal, 0)
        );
    }

    // ── Scoring table ────────────────────────────────────────────────

    private static Map<Signal, Integer> scoreOf(MatchingAnswer answer) {
        Map<Signal, Integer> m = new EnumMap<>(Signal.class);
        switch (answer) {
            // Q1
            case LIFE_GOT_BUSY      -> m.put(Signal.CONSISTENCY, 2);
            case LOST_MOTIVATION    -> m.put(Signal.INTENSITY, -1);
            case NO_ONE_NOTICED     -> { m.put(Signal.SOCIAL_PULL, 1); m.put(Signal.INVISIBILITY_WOUND, 3); }
            case INJURY_OR_HEALTH   -> m.put(Signal.CONSISTENCY, 1);
            // Q2
            case CELEBRATE_WINS     -> { m.put(Signal.SOCIAL_PULL, 2); m.put(Signal.INVISIBILITY_WOUND, 1); }
            case PUSH_WHEN_SLACKING -> m.put(Signal.INTENSITY, 2);
            case BALANCED_MIX       -> { m.put(Signal.SOCIAL_PULL, 1); m.put(Signal.INTENSITY, 1); }
            case DOESNT_MATTER      -> { /* no signal */ }
            // Q3
            case SHOW_UP_ON_BAD_DAYS -> { m.put(Signal.CONSISTENCY, 3); m.put(Signal.INTENSITY, 1); }
            case NEED_NUDGING        -> { m.put(Signal.CONSISTENCY, 1); m.put(Signal.SOCIAL_PULL, 1); }
            case GO_THROUGH_PHASES   -> m.put(Signal.INVISIBILITY_WOUND, 1);
            case STILL_FIGURING_OUT  -> m.put(Signal.CONSISTENCY, -1);
            // Q4
            case SOCIAL_PRESSURE          -> { m.put(Signal.SOCIAL_PULL, 1); m.put(Signal.INVISIBILITY_WOUND, 1); m.put(Signal.INTENSITY, 1); }
            case FRIENDLY_COMPETITION     -> m.put(Signal.INTENSITY, 3);
            case SHARED_PROGRESS_TRACKING -> { m.put(Signal.CONSISTENCY, 1); m.put(Signal.SOCIAL_PULL, 1); }
            case SOMEONE_CARES            -> { m.put(Signal.SOCIAL_PULL, 1); m.put(Signal.INVISIBILITY_WOUND, 2); }
        }
        return m;
    }

    @SafeVarargs
    private static int sumSignal(Signal s, Map<Signal, Integer>... maps) {
        int total = 0;
        for (Map<Signal, Integer> m : maps) total += m.getOrDefault(s, 0);
        return total;
    }

    private static void requireQuestion(MatchingAnswer a, MatchingQuestion expected) {
        if (a == null) throw new IllegalArgumentException("answer is null");
        if (a.question() != expected) {
            throw new IllegalArgumentException(
                    "Answer " + a + " belongs to " + a.question() + ", not " + expected);
        }
    }
}
