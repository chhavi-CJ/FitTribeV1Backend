-- V22: personal_records table — one row per user per exercise, always the best weight

CREATE TABLE IF NOT EXISTS personal_records (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id VARCHAR(50) NOT NULL,
    weight_kg   DECIMAL(6,2) NOT NULL,
    reps        INT NOT NULL,
    achieved_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, exercise_id)
);

CREATE INDEX IF NOT EXISTS idx_pr_user ON personal_records(user_id);
