-- Make phone nullable (Google/Apple users won't have one)
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;

-- Add email column (nullable, unique when present via partial index)
ALTER TABLE users ADD COLUMN email VARCHAR(320);
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email)) WHERE email IS NOT NULL;

-- Add auth provider tracking
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20);
ALTER TABLE users ADD COLUMN auth_providers VARCHAR(80);
-- auth_provider = primary signup provider (PHONE, EMAIL, GOOGLE, APPLE)
-- auth_providers = comma-separated list of all linked providers (e.g., "PHONE,GOOGLE")

-- At least one identifier must exist
ALTER TABLE users ADD CONSTRAINT chk_user_has_identifier
  CHECK (phone IS NOT NULL OR email IS NOT NULL OR firebase_uid IS NOT NULL);

-- Backfill existing users (all current users are PHONE provider)
UPDATE users SET auth_provider = 'PHONE', auth_providers = 'PHONE' WHERE auth_provider IS NULL;
