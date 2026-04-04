-- V25: user settings preferences and soft-delete support

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS notifications_enabled  BOOLEAN   NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS show_in_leaderboard    BOOLEAN   NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS is_active              BOOLEAN   NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS deletion_requested_at  TIMESTAMP NULL;
