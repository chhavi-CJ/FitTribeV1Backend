-- V46: Drop the legacy personal_records table.
--
-- Context:
--   personal_records (V22) was the original PR storage. PR System V2 (V44)
--   introduced pr_events + user_exercise_bests as the authoritative
--   replacement. All readers migrated to the new system in this same PR.
--
--   The writer for personal_records (PersonalRecordRepository.upsertPr)
--   had no callers — confirmed via grep before this PR. Existing data
--   in the table is legacy/test-only; no real user data is lost.
--
-- Cascade impact:
--   pr_events.user_id and user_exercise_bests.user_id both have
--   ON DELETE CASCADE to users(id). User deletion (via
--   AccountPurgeScheduler) continues to clean up PR data without
--   this table.

DROP TABLE IF EXISTS personal_records CASCADE;
