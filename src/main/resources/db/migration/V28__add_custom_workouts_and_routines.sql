-- Add source tracking to workout_sessions
ALTER TABLE workout_sessions
  ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'AI_PLAN';

ALTER TABLE workout_sessions
  ADD COLUMN IF NOT EXISTS source_routine_id UUID NULL;

ALTER TABLE workout_sessions
  ADD COLUMN IF NOT EXISTS planned_exercises JSONB NULL;

-- Create saved_routines table
CREATE TABLE IF NOT EXISTS saved_routines (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL,
  name            VARCHAR(100) NOT NULL,
  exercises       JSONB NOT NULL,
  times_used      INTEGER NOT NULL DEFAULT 0,
  last_used_at    TIMESTAMPTZ NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_routines_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for routines
CREATE INDEX IF NOT EXISTS idx_routines_user
  ON saved_routines(user_id);

CREATE INDEX IF NOT EXISTS idx_routines_user_recent
  ON saved_routines(user_id, last_used_at DESC NULLS LAST);

-- FK from workout_sessions.source_routine_id to saved_routines.id
-- ON DELETE SET NULL preserves historical sessions when routine is deleted
ALTER TABLE workout_sessions
  ADD CONSTRAINT fk_session_routine
  FOREIGN KEY (source_routine_id) REFERENCES saved_routines(id) ON DELETE SET NULL;

-- Index for source-based queries (e.g. "show me all my custom workouts")
CREATE INDEX IF NOT EXISTS idx_sessions_user_source
  ON workout_sessions(user_id, source);
