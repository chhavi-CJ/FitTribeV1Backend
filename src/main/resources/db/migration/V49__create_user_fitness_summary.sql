-- V49: user_fitness_summary — pre-computed training history snapshot per user.
-- Refreshed nightly at 3am IST by FitnessSummaryScheduler.
-- One row per user (1:1 with users table, upserted each night).

CREATE TABLE user_fitness_summary (
    user_id        UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    summary        JSONB       NOT NULL,
    computed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    sample_window  TEXT        NOT NULL
);

COMMENT ON TABLE user_fitness_summary IS
    'Pre-computed training history snapshot per user. Refreshed nightly at 3am IST by FitnessSummaryScheduler.';

COMMENT ON COLUMN user_fitness_summary.summary IS
    'JSONB payload with mainLiftStrength, muscleGroupVolume, weeklyConsistency, rpeTrend, prActivity, lastTrainedByMuscle.';

COMMENT ON COLUMN user_fitness_summary.sample_window IS
    'Human-readable window description for debugging, e.g. "2026-03-27 to 2026-04-22".';
