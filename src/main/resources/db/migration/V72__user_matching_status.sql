-- V72: Conscious Matching — user_matching_status enum on users.
-- Tracks each user's relationship with the matching feature.
--
--   NONE       — default, never engaged with matching.
--   QUEUED     — answered the matching quiz, eligible for next batch run.
--                Unmatched users (deferred + remainder) also remain QUEUED
--                after a batch — simplifies the eligible-pool query to a
--                single-value filter.
--   MATCHED    — placed in a matched group. Flipped back to QUEUED if the
--                user leaves their matched group and re-takes the quiz.
--   OPTED_OUT  — user explicitly opted out (future; not set by any current
--                code path but reserved in the enum).
--
-- Existing users default to NONE. The CHECK constraint mirrors the Java
-- enum exactly — keep them in sync if either changes.

ALTER TABLE users
  ADD COLUMN user_matching_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

ALTER TABLE users
  ADD CONSTRAINT users_matching_status_chk
  CHECK (user_matching_status IN ('NONE', 'QUEUED', 'MATCHED', 'OPTED_OUT'));

-- Index supports the batch run's eligible-pool query:
--   SELECT ... FROM users WHERE user_matching_status = 'QUEUED'
-- Partial index keeps it tiny — only QUEUED rows are in scope.
CREATE INDEX idx_users_matching_status_queued
  ON users(user_matching_status)
  WHERE user_matching_status = 'QUEUED';
