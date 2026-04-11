-- V34 — create pending_recalibrations table
-- Wynners Implementation Plan v1.1 — step A1.4
--
-- Side table for weight-target adjustments queued by the weekly report
-- findings engine (single_too_light / multiple_too_light / pr_regression,
-- etc). Rows are inserted by WeeklyReportService when a finding fires,
-- then consumed by the next AI plan generator run for the user's upcoming
-- week. When the generator applies a recalibration, it marks the row with
-- applied_at=NOW() and applied_to_plan_id=<new plan>.
--
-- Why a side table instead of mutating user_plans.days JSONB in place:
-- keeps the plan document immutable after generation, makes the "what
-- we're fixing" section on the Weekly Summary screen derivable from
-- weekly_reports.recalibrations JSONB + this table without touching
-- historical plans.

CREATE TABLE pending_recalibrations (
  id                  BIGSERIAL     PRIMARY KEY,
  user_id             UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  exercise_id         VARCHAR(50)   NOT NULL,
  old_target_kg       NUMERIC(6,2),
  new_target_kg       NUMERIC(6,2)  NOT NULL,
  reason              TEXT          NOT NULL,
  applied_to_plan_id  UUID          REFERENCES user_plans(plan_id),
  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  applied_at          TIMESTAMPTZ
);

-- Partial index — only indexes rows still awaiting application by the
-- next plan generator run. The generator query is
--   SELECT ... WHERE user_id = ? AND applied_at IS NULL
-- so indexing just the unapplied rows keeps the lookup cheap as history
-- accumulates. Once applied_at is set, the row drops out of the index.
CREATE INDEX idx_recalibrations_user_unapplied
  ON pending_recalibrations (user_id)
  WHERE applied_at IS NULL;
