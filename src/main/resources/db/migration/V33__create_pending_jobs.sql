-- V33 — create pending_jobs table
-- Wynners Implementation Plan v1.1 — step A1.2
--
-- Generic async job queue. Rows are inserted by producers (e.g. weekly
-- report trigger on session finish), picked up by a scheduled worker
-- that looks for status='pending' AND scheduled_for <= NOW(), marks them
-- 'running', and writes 'completed' or 'failed' (with error text) when
-- done. The partial pickup index keeps the hot-path scan cheap even as
-- completed rows accumulate.

CREATE TABLE pending_jobs (
  id             BIGSERIAL     PRIMARY KEY,
  job_type       VARCHAR(50)   NOT NULL,
  payload        JSONB         NOT NULL DEFAULT '{}',
  status         VARCHAR(20)   NOT NULL DEFAULT 'pending',
  attempts       INT           NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  scheduled_for  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  started_at     TIMESTAMPTZ,
  completed_at   TIMESTAMPTZ,
  error          TEXT
);

-- Partial index — only indexes rows still awaiting pickup. The worker
-- query is ORDER BY scheduled_for LIMIT N WHERE status='pending', so
-- (status, scheduled_for) is the right shape. As jobs complete they
-- drop out of the index, keeping it compact regardless of history size.
CREATE INDEX idx_pending_jobs_pickup
  ON pending_jobs (status, scheduled_for)
  WHERE status = 'pending';
