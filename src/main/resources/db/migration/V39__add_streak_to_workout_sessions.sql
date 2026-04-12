-- V39: Add streak snapshot column to workout_sessions
-- Captures the user's current streak at the time of session finish.
-- Used by the /history endpoint to show "what was the streak when you finished this workout".

ALTER TABLE workout_sessions ADD COLUMN IF NOT EXISTS streak INT;
