-- V56: Add structured event_data payload column to feed_items.
-- Allows the GET /api/v1/groups/{id}/feed endpoint to return rich
-- event cards (session stats, PR lifts, tier info, etc.) without
-- requiring callers to JOIN other tables.
-- Schema of the JSONB object varies by feed_items.type:
--   WORKOUT_FINISHED   -> { sessionId, durationMins, totalVolumeKg, muscleGroups[], topLiftKg, sets }
--   PR_DETECTED        -> { sessionId, prCount, lifts[{ exerciseName, prCategory, deltaKg, newBestKg, newBestReps }] }
--   TIER_LOCKED        -> { tier, sessionsLogged, targetSessions, overachiever, streakAtLock }
--   TOP_PERFORMER_CROWNED -> { winnerUserId, dimension, scoreValue, metricLabel }
--   STATUS_CHANGED     -> { date, newStatus, previousStatus }

ALTER TABLE feed_items
    ADD COLUMN event_data JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN feed_items.event_data IS
    'Structured payload for feed event rendering (session_id, pr_count, tier, lifts array, etc.). Schema varies by feed_items.type.';
