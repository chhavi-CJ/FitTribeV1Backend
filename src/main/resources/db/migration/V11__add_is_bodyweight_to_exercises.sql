-- V11: Add is_bodyweight flag to exercises catalog
ALTER TABLE exercises
  ADD COLUMN IF NOT EXISTS is_bodyweight BOOLEAN NOT NULL DEFAULT FALSE;

-- Mark known bodyweight exercises
UPDATE exercises SET is_bodyweight = TRUE
WHERE id IN ('pull-ups', 'push-ups', 'dips', 'plank', 'crunches', 'squats');
