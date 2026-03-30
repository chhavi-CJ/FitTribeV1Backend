-- V12: Add ai_context field to users for personalised AI plan generation
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS ai_context VARCHAR(500);
