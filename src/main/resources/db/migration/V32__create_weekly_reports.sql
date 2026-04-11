-- V32 — create weekly_reports table
-- Wynners Implementation Plan v1.1 — step A1.1
--
-- Stores one computed weekly report per (user, week_start). Written by
-- WeeklyReportService.generateWeeklyReport() when a user hits their weekly
-- goal on session finish. The JSONB columns carry the five sub-structures
-- consumed by the Weekly Summary screen; scalar columns are the fast-path
-- values for list views and the "Week N report ready" card on Progress.

CREATE TABLE weekly_reports (
  id                BIGSERIAL     PRIMARY KEY,
  user_id           UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  week_start        DATE          NOT NULL,
  week_end          DATE          NOT NULL,
  week_number       INT           NOT NULL,

  user_first_name   VARCHAR(100)  NOT NULL,
  sessions_logged   INT           NOT NULL,
  sessions_goal     INT           NOT NULL,
  total_kg_volume   NUMERIC(10,2) NOT NULL DEFAULT 0,
  pr_count          INT           NOT NULL DEFAULT 0,

  verdict           TEXT,

  personal_records  JSONB         NOT NULL DEFAULT '[]',
  baselines         JSONB         NOT NULL DEFAULT '[]',
  muscle_coverage   JSONB         NOT NULL DEFAULT '[]',
  findings          JSONB         NOT NULL DEFAULT '[]',
  recalibrations    JSONB         NOT NULL DEFAULT '[]',

  is_week_one       BOOLEAN       NOT NULL DEFAULT false,
  computed_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  schema_version    INT           NOT NULL DEFAULT 1,

  CONSTRAINT uq_weekly_reports_user_week UNIQUE (user_id, week_start)
);

CREATE INDEX idx_weekly_reports_user_week
  ON weekly_reports (user_id, week_start DESC);
