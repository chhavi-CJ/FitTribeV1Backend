-- V76: Add status tracking to daily_plan_generated for async generation
-- Tracks whether AI plan generation is PENDING, READY, or FAILED
-- Default 'READY' so existing rows (already generated) are unaffected

ALTER TABLE daily_plan_generated ADD COLUMN status VARCHAR(16) DEFAULT 'READY';

-- PENDING: OpenAI call is in flight; exercises field is empty/null
-- READY: OpenAI call completed successfully; exercises field is populated
-- FAILED: OpenAI call failed; client can retry via POST /plan/today/generate
