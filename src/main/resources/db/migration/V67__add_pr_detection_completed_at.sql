-- Marker column written at the end of PrWritePathService.processSessionFinish.
-- NULL means "PR detection has not run (or has not completed) for this session".
-- Used by GET /sessions/today to expose prDetectionComplete=true|false to the
-- frontend so the summary screen can distinguish "still detecting" from
-- "detection ran, no PR" while async PR detection is in flight.
--
-- No backfill needed — NULL for existing rows is the correct semantics for
-- "we don't know if PR detection ran" on rows that finished before this PR.
-- Frontend treats null as "not complete" and falls back to whatever isPr the
-- backend currently has, which is the only behaviour those rows ever had.
ALTER TABLE workout_sessions
    ADD COLUMN pr_detection_completed_at TIMESTAMPTZ NULL;
