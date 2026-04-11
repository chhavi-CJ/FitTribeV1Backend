-- Append-only weekly muscle strength scores (0–100, Epley-based).
-- One row per (user, muscle, week_start). The UNIQUE constraint makes
-- every recompute idempotent via ON CONFLICT DO UPDATE in the repository.
CREATE TABLE strength_score_history (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- One of: CHEST | BACK | LEGS | SHOULDERS | BICEPS | TRICEPS
    muscle          VARCHAR(20)  NOT NULL,
    week_start      DATE         NOT NULL,
    -- Normalised 0–100 score computed from Epley 1RM vs target 1RM.
    strength_score  INT          NOT NULL,
    -- formula_version: must stay in lockstep with exercise_strength_targets.formula_version.
    -- Bump both columns simultaneously when the scoring formula changes.
    formula_version INT          NOT NULL DEFAULT 1,
    computed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_strength_score_user_muscle_week
        UNIQUE (user_id, muscle, week_start)
);

CREATE INDEX idx_strength_score_history_user_week
    ON strength_score_history (user_id, week_start DESC);
