-- V26: streak freeze balance on users, crown expiry on group_members

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS streak_freeze_balance INT NOT NULL DEFAULT 0;

ALTER TABLE group_members
  ADD COLUMN IF NOT EXISTS crown_expires_at TIMESTAMP NULL;
