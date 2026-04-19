-- V45: Enforce set_id NOT NULL on pr_events.
--
-- Context:
--   pr_events.set_id was nullable due to an early write path that did not
--   persist it. Both current write paths (PrWritePathService line 136 and
--   PrEditCascadeService line 246) correctly set it. No NULL-set_id rows
--   have been created since 2026-04-15.
--
-- Cleanup:
--   Deletes the 8 remaining NULL-set_id test rows (all from Kavya's test
--   user, created during manual QA on Apr 14-15). No real user data is
--   affected.
--
-- Safety:
--   Wrapped in a transaction. Aborts if more than 10 NULL rows exist,
--   protecting against surprise data changes between authoring and deploy.

BEGIN;

DO $$
DECLARE
    null_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO null_count FROM pr_events WHERE set_id IS NULL;
    IF null_count > 10 THEN
        RAISE EXCEPTION
            'Expected at most 10 NULL set_id rows in pr_events, found %. '
            'Investigate before running this migration.', null_count;
    END IF;
END $$;

DELETE FROM pr_events WHERE set_id IS NULL;

ALTER TABLE pr_events ALTER COLUMN set_id SET NOT NULL;

COMMIT;
