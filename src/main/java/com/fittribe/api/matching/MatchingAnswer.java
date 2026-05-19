package com.fittribe.api.matching;

/**
 * Every possible answer to the four matching questions (PRD §3).
 * Each value is tagged with the question it belongs to so the classifier
 * can reject mismatched combinations (e.g. submitting a Q1 answer as Q3).
 *
 * The string form (name()) of these enums is what gets persisted into
 * matching_profile.answer_q1..q4 — so renaming a value is a breaking
 * change once users start submitting answers.
 */
public enum MatchingAnswer {
    // Q1 — break reason
    LIFE_GOT_BUSY            (MatchingQuestion.Q1_BREAK_REASON),
    LOST_MOTIVATION          (MatchingQuestion.Q1_BREAK_REASON),
    NO_ONE_NOTICED           (MatchingQuestion.Q1_BREAK_REASON),
    INJURY_OR_HEALTH         (MatchingQuestion.Q1_BREAK_REASON),

    // Q2 — group energy
    CELEBRATE_WINS           (MatchingQuestion.Q2_GROUP_ENERGY),
    PUSH_WHEN_SLACKING       (MatchingQuestion.Q2_GROUP_ENERGY),
    BALANCED_MIX             (MatchingQuestion.Q2_GROUP_ENERGY),
    DOESNT_MATTER            (MatchingQuestion.Q2_GROUP_ENERGY),

    // Q3 — consistency
    SHOW_UP_ON_BAD_DAYS      (MatchingQuestion.Q3_CONSISTENCY),
    NEED_NUDGING             (MatchingQuestion.Q3_CONSISTENCY),
    GO_THROUGH_PHASES        (MatchingQuestion.Q3_CONSISTENCY),
    STILL_FIGURING_OUT       (MatchingQuestion.Q3_CONSISTENCY),

    // Q4 — motivation
    SOCIAL_PRESSURE          (MatchingQuestion.Q4_MOTIVATION),
    FRIENDLY_COMPETITION     (MatchingQuestion.Q4_MOTIVATION),
    SHARED_PROGRESS_TRACKING (MatchingQuestion.Q4_MOTIVATION),
    SOMEONE_CARES            (MatchingQuestion.Q4_MOTIVATION);

    private final MatchingQuestion question;

    MatchingAnswer(MatchingQuestion question) { this.question = question; }

    public MatchingQuestion question() { return question; }
}
