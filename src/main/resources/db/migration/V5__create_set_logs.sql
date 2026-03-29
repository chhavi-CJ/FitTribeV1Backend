-- V5: set_logs
CREATE TABLE set_logs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID         NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id   VARCHAR(50)  NOT NULL REFERENCES exercises(id),
    exercise_name VARCHAR(100),
    set_number    INT          NOT NULL,
    weight_kg     DECIMAL(6,2),
    reps          INT,
    is_pr         BOOLEAN      DEFAULT FALSE,
    logged_at     TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_set_logs_session_id  ON set_logs(session_id);
CREATE INDEX idx_set_logs_exercise_id ON set_logs(exercise_id);
CREATE INDEX idx_set_logs_is_pr       ON set_logs(is_pr) WHERE is_pr = TRUE;
