-- V42: bonus_session_generated table
-- Stores AI-generated bonus sessions separately from daily_plan_generated.
-- Bonus sessions are triggered after a user hits their weekly goal.
-- Composite PK (user_id, date, bonus_number) supports multiple bonuses
-- per day (rare but possible — morning + evening) while keeping one
-- row per generation request deterministic.

CREATE TABLE bonus_session_generated (
  user_id             UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  date                DATE      NOT NULL,
  bonus_number        INTEGER   NOT NULL,
  archetype           VARCHAR(32) NOT NULL,
  archetype_rationale TEXT,
  exercises           JSONB     NOT NULL,
  session_note        TEXT,
  day_coach_tip       TEXT,
  generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  PRIMARY KEY (user_id, date, bonus_number)
);

CREATE INDEX idx_bonus_user_date ON bonus_session_generated(user_id, date DESC);
