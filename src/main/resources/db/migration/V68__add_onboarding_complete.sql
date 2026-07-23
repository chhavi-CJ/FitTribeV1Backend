-- Add onboarding_complete flag to users
-- Default false for new rows. Existing rows are backfilled true ONLY if they
-- have all the required onboarding fields populated; otherwise false (they'll
-- be forced through onboarding again or cleaned up by the scheduler).

ALTER TABLE users
  ADD COLUMN onboarding_complete BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users
  SET onboarding_complete = TRUE
  WHERE display_name IS NOT NULL
    AND gender IS NOT NULL
    AND goal IS NOT NULL
    AND fitness_level IS NOT NULL
    AND height_cm IS NOT NULL
    AND weight_kg IS NOT NULL;

CREATE INDEX idx_users_onboarding_complete_created_at
  ON users (onboarding_complete, created_at)
  WHERE onboarding_complete = FALSE;
