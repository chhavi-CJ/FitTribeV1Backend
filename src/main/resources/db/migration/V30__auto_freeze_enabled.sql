-- V30: Allow users to opt out of automatic streak freeze consumption
ALTER TABLE users ADD COLUMN IF NOT EXISTS auto_freeze_enabled BOOLEAN NOT NULL DEFAULT TRUE;
