-- V13: User session feedback — rating after each completed workout
CREATE TABLE user_session_feedback (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id    UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
  rating        VARCHAR(20) NOT NULL
                CHECK (rating IN ('KILLED_ME', 'HARD', 'GOOD', 'TOO_EASY')),
  notes         VARCHAR(200),
  created_at    TIMESTAMP NOT NULL DEFAULT now(),
  UNIQUE(session_id)
);

CREATE INDEX idx_session_feedback_user    ON user_session_feedback(user_id);
CREATE INDEX idx_session_feedback_session ON user_session_feedback(session_id);
