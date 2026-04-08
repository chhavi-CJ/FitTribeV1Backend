-- V29: Replace single-column user index with composite (user_id, created_at DESC)
--      and add updated_at for upsert support
DROP INDEX IF EXISTS idx_session_feedback_user;

CREATE INDEX idx_session_feedback_user_time
    ON user_session_feedback(user_id, created_at DESC);

ALTER TABLE user_session_feedback
    ADD COLUMN updated_at TIMESTAMP;
