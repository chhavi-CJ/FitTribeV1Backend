-- V40: Bonus session support infrastructure
--
-- Adds session_archetype column to workout_sessions so that both planned
-- sessions (from split_template_days) and bonus sessions can be categorised
-- uniformly for reporting and analysis.
--
-- Archetype enum values (enforced at application layer):
--   PUSH, PULL, LEGS, UPPER, LOWER, FULL_BODY          — planned session types
--   ACCESSORY_CORE, WEAK_POINT_FOCUS, CONDITIONING,
--   MOBILITY_CARDIO                                     — bonus session types

ALTER TABLE workout_sessions
  ADD COLUMN IF NOT EXISTS session_archetype VARCHAR(32) NULL;

COMMENT ON COLUMN workout_sessions.session_archetype IS
  'Session category. Planned values: PUSH/PULL/LEGS/UPPER/LOWER/FULL_BODY. Bonus values: ACCESSORY_CORE/WEAK_POINT_FOCUS/CONDITIONING/MOBILITY_CARDIO.';

-- Backfill archetype for existing AI_PLAN rows from session name.
-- Only runs on rows where archetype is currently NULL and source is AI_PLAN,
-- so it's idempotent and won't touch manually-set values.
UPDATE workout_sessions
SET session_archetype = CASE
    WHEN LOWER(name) LIKE '%push%'      THEN 'PUSH'
    WHEN LOWER(name) LIKE '%pull%'      THEN 'PULL'
    WHEN LOWER(name) LIKE '%leg%'       THEN 'LEGS'
    WHEN LOWER(name) LIKE '%upper%'     THEN 'UPPER'
    WHEN LOWER(name) LIKE '%lower%'     THEN 'LOWER'
    WHEN LOWER(name) LIKE '%full body%' THEN 'FULL_BODY'
    WHEN LOWER(name) LIKE '%rest%'      THEN 'MOBILITY_CARDIO'
    ELSE NULL
  END
WHERE session_archetype IS NULL
  AND source = 'AI_PLAN'
  AND name IS NOT NULL;

-- Index for recovery gate queries: "last completed session per user ordered by finish time"
-- Partial index on COMPLETED is smaller and faster than full index.
CREATE INDEX IF NOT EXISTS idx_sessions_user_finished_desc
  ON workout_sessions(user_id, finished_at DESC)
  WHERE status = 'COMPLETED';

-- Index for bonus-specific queries: count bonuses per week, list bonus archetypes for reports.
CREATE INDEX IF NOT EXISTS idx_sessions_user_source_finished
  ON workout_sessions(user_id, source, finished_at DESC)
  WHERE status = 'COMPLETED';
