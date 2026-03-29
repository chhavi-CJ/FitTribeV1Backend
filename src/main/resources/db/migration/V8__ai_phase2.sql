-- V8: AI Phase 2 — new tables + alter existing

-- 1. New table: user_plans
CREATE TABLE user_plans (
  plan_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL REFERENCES users(id),
  week_start_date   DATE NOT NULL,
  week_number       INT NOT NULL,
  days              JSONB NOT NULL,
  ai_rationale      JSONB,
  generated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_user_plans_user_id ON user_plans(user_id);
CREATE UNIQUE INDEX idx_user_plans_user_week ON user_plans(user_id, week_start_date);

-- 2. New table: ai_insights
CREATE TABLE ai_insights (
  insight_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            UUID NOT NULL REFERENCES users(id),
  generated_at       TIMESTAMPTZ DEFAULT NOW(),
  insight_type       VARCHAR(50),
  muscle_group       VARCHAR(50),
  exercise_name      VARCHAR(100),
  finding            TEXT,
  user_action_taken  VARCHAR(20),
  applied_to_plan_id UUID REFERENCES user_plans(plan_id)
);

CREATE INDEX idx_ai_insights_user_id ON ai_insights(user_id);
CREATE INDEX idx_ai_insights_type    ON ai_insights(user_id, insight_type);

-- 3. Alter workout_sessions
ALTER TABLE workout_sessions
  ADD COLUMN IF NOT EXISTS ai_planned_weights  JSONB,
  ADD COLUMN IF NOT EXISTS exercises_skipped   TEXT[],
  ADD COLUMN IF NOT EXISTS exercises_added     JSONB,
  ADD COLUMN IF NOT EXISTS weekly_goal_hit     BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS week_number         INT;

-- 4. Alter exercises
ALTER TABLE exercises
  ADD COLUMN IF NOT EXISTS demo_video_url     VARCHAR(500),
  ADD COLUMN IF NOT EXISTS muscle_diagram_url VARCHAR(500),
  ADD COLUMN IF NOT EXISTS steps              TEXT[],
  ADD COLUMN IF NOT EXISTS common_mistakes    TEXT[],
  ADD COLUMN IF NOT EXISTS coach_tip          TEXT,
  ADD COLUMN IF NOT EXISTS secondary_muscles  TEXT[],
  ADD COLUMN IF NOT EXISTS swap_alternatives  TEXT[];

-- 5. Seed swap_alternatives
UPDATE exercises SET swap_alternatives = ARRAY['DB flat press','Smith machine bench','Push-ups']                        WHERE id = 'bench-press';
UPDATE exercises SET swap_alternatives = ARRAY['Standing overhead press','Arnold press','Machine shoulder press']       WHERE id = 'shoulder-press';
UPDATE exercises SET swap_alternatives = ARRAY['Incline barbell press','Cable incline fly','Incline push-ups']         WHERE id = 'incline-db-press';
UPDATE exercises SET swap_alternatives = ARRAY['Cable lateral raise','Machine lateral raise','Behind-back cable raise'] WHERE id = 'lateral-raises';
UPDATE exercises SET swap_alternatives = ARRAY['Tricep overhead extension','Close-grip bench press','Tricep dips']      WHERE id = 'tricep-pushdowns';
UPDATE exercises SET swap_alternatives = ARRAY['Pull-ups','Seated cable row','Single-arm DB row']                      WHERE id = 'lat-pulldown';
UPDATE exercises SET swap_alternatives = ARRAY['Barbell row','T-bar row','Dumbbell row']                               WHERE id = 'barbell-row';
UPDATE exercises SET swap_alternatives = ARRAY['Barbell squat','Hack squat','Bulgarian split squat']                   WHERE id = 'leg-press';
UPDATE exercises SET swap_alternatives = ARRAY['Leg press','Hack squat','Goblet squat']                               WHERE id = 'squat';
UPDATE exercises SET swap_alternatives = ARRAY['Lying leg curl','Nordic curl','Seated leg curl']                       WHERE id = 'romanian-deadlift';
UPDATE exercises SET swap_alternatives = ARRAY['Romanian deadlift','Nordic curl','Swiss ball curl']                    WHERE id = 'leg-curl';
UPDATE exercises SET swap_alternatives = ARRAY['Lat pulldown','Assisted pull-ups','Cable pull-over']                   WHERE id = 'pull-ups';
UPDATE exercises SET swap_alternatives = ARRAY['Hammer curl','Preacher curl','Cable curl']                             WHERE id = 'bicep-curl';
UPDATE exercises SET swap_alternatives = ARRAY['DB fly','Pec deck machine','Push-up fly']                              WHERE id = 'cable-flyes';
UPDATE exercises SET swap_alternatives = ARRAY['Bench press','Diamond push-ups','Pike push-ups']                       WHERE id = 'push-ups';
UPDATE exercises SET swap_alternatives = ARRAY['Tricep pushdown','Skull crushers','Close-grip push-up']                WHERE id = 'dips';
UPDATE exercises SET swap_alternatives = ARRAY['Dumbbell shoulder press','Push press','Landmine press']                WHERE id = 'overhead-press';
UPDATE exercises SET swap_alternatives = ARRAY['Dead bug','Bird dog','Ab wheel rollout']                               WHERE id = 'plank';
UPDATE exercises SET swap_alternatives = ARRAY['Cable crunch','Decline crunch','Sit-ups']                              WHERE id = 'crunches';
