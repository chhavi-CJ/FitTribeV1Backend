-- V48: Composite indexes for GET /sessions/today and related queries.
--
-- Context:
--   The /today endpoint fires 5+ queries. Three are using separate
--   single-column indexes that force Postgres to index-scan then sort
--   or filter in memory. Composite indexes let Postgres satisfy each
--   query from a single index scan.
--
-- Audit: TODAY_ENDPOINT_PERF_AUDIT.md (2026-04-20)
--
-- Note: not using CREATE INDEX CONCURRENTLY because:
--   (a) Flyway wraps SQL migrations in transactions — CONCURRENTLY
--       cannot run inside a transaction block.
--   (b) Tables are small at current scale (<200 rows in
--       workout_sessions, <50 in pr_events). Regular CREATE INDEX
--       locks the table for milliseconds.
--   (c) Migration runs at deploy time with zero concurrent traffic.
--   Revisit if table sizes exceed 100k rows.

-- 1. Session lookup by user + started_at range (GET /sessions/today).
--    Replaces separate idx_sessions_user_id + idx_sessions_started_at
--    for the query: WHERE user_id = ? AND started_at BETWEEN ? AND ?
--    ORDER BY started_at DESC LIMIT 1
CREATE INDEX IF NOT EXISTS idx_ws_user_started
    ON workout_sessions(user_id, started_at DESC);

-- 2. Weekly completion count: WHERE user_id = ? AND status = 'COMPLETED'
--    AND finished_at BETWEEN ? AND ?
--    Partial index: every caller passes status = 'COMPLETED' (confirmed
--    via grep — 6 call sites, all hardcode 'COMPLETED'). Partial index
--    is smaller and faster than a full composite.
CREATE INDEX IF NOT EXISTS idx_ws_user_completed_finished
    ON workout_sessions(user_id, finished_at DESC)
    WHERE status = 'COMPLETED';

-- 3. PR events by session with active-only filter:
--    WHERE session_id = ? AND superseded_at IS NULL
--    Partial index avoids scanning superseded rows.
CREATE INDEX IF NOT EXISTS idx_pr_events_session_active
    ON pr_events(session_id)
    WHERE superseded_at IS NULL;
