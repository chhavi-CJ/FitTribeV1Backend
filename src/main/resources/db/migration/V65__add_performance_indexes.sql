CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_finished 
  ON workout_sessions(user_id, finished_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_session_feedback_user_created 
  ON user_session_feedback(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_plans_user_week 
  ON user_plans(user_id, week_start_date DESC);
