-- Profile endpoint counts on workout_sessions:
-- countByUserIdAndStatus, countByUserIdAndStatusAndFinishedAtBetween,
-- countDistinctTrainingDays
CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_status_finished
  ON workout_sessions (user_id, status, finished_at DESC);

-- Profile endpoint count on user_exercise_bests:
-- countByUserId
CREATE INDEX IF NOT EXISTS idx_user_exercise_bests_user_id
  ON user_exercise_bests (user_id);

-- User session feedback by user / recency
CREATE INDEX IF NOT EXISTS idx_user_session_feedback_user_created
  ON user_session_feedback (user_id, created_at DESC);
