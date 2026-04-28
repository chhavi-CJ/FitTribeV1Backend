-- V55: Top Performer leaderboard — Phase 1 (Effort Score dimension only)

-- ── 1. New columns on users ───────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_baseline_computed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS leaderboard_eligible       BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS timezone                   VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN IF NOT EXISTS pause_until                TIMESTAMP;

-- ── 2. user_weekly_stats ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_weekly_stats (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start_date          DATE          NOT NULL,
    sessions_count           INTEGER       NOT NULL DEFAULT 0,
    total_volume_kg          DECIMAL(10,2) NOT NULL DEFAULT 0,
    prs_hit                  INTEGER       NOT NULL DEFAULT 0,
    weekly_goal_target       INTEGER       NOT NULL,
    weekly_goal_hit          BOOLEAN       NOT NULL DEFAULT false,
    sessions_with_3plus_sets INTEGER       NOT NULL DEFAULT 0,
    sessions_45min_plus      INTEGER       NOT NULL DEFAULT 0,
    baseline_volume_kg       DECIMAL(10,2),
    computed_at              TIMESTAMP     NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, week_start_date)
);

CREATE INDEX IF NOT EXISTS idx_uws_user_week ON user_weekly_stats(user_id, week_start_date DESC);
CREATE INDEX IF NOT EXISTS idx_uws_week      ON user_weekly_stats(week_start_date);

-- ── 3. group_weekly_top_performer ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS group_weekly_top_performer (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id       UUID        NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    iso_year       INTEGER     NOT NULL,
    iso_week       INTEGER     NOT NULL,
    winner_user_id UUID        NOT NULL REFERENCES users(id),
    dimension      VARCHAR(20) NOT NULL,
    score_value    INTEGER     NOT NULL,
    metric_label   TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE(group_id, iso_year, iso_week, dimension)
);

CREATE INDEX IF NOT EXISTS idx_gwtp_group_week
    ON group_weekly_top_performer(group_id, iso_year DESC, iso_week DESC);

-- ── 4. Deferred session index (backlog item) ──────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_ws_user_finished
    ON workout_sessions(user_id, finished_at DESC);
