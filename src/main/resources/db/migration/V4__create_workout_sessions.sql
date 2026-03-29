-- V4: workout_sessions
CREATE TABLE workout_sessions (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                     VARCHAR(100),
    badge                    VARCHAR(50),
    status                   VARCHAR(20)  DEFAULT 'IN_PROGRESS',
    exercises                JSONB,
    total_sets               INT          DEFAULT 0,
    total_volume_kg          DECIMAL(10,2) DEFAULT 0,
    duration_mins            INT,
    started_at               TIMESTAMPTZ  DEFAULT NOW(),
    finished_at              TIMESTAMPTZ,
    ai_insight               TEXT,
    ai_insight_generated_at  TIMESTAMPTZ
);

CREATE INDEX idx_sessions_user_id    ON workout_sessions(user_id);
CREATE INDEX idx_sessions_status     ON workout_sessions(status);
CREATE INDEX idx_sessions_started_at ON workout_sessions(started_at DESC);
