-- V20: unique constraint for upsert + performance indexes

-- Unique constraint enabling ON CONFLICT upsert in set_logs
ALTER TABLE set_logs
    ADD CONSTRAINT uq_set_logs_session_exercise_set
    UNIQUE (session_id, exercise_id, set_number);

-- Speed up set_logs lookups by session
CREATE INDEX IF NOT EXISTS idx_set_logs_session_id
    ON set_logs (session_id);

-- Speed up session history and cooldown queries
CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_finished
    ON workout_sessions (user_id, finished_at DESC);

-- GIN index for JSONB exercises column
CREATE INDEX IF NOT EXISTS idx_workout_sessions_exercises
    ON workout_sessions USING GIN (exercises);
