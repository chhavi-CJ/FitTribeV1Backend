-- V69: enable hard-delete of user accounts
-- Four FKs that reference rows owned by a user currently default to
-- NO ACTION, which blocks DELETE FROM users for any real user with AI
-- insights, plans, or groups they created. Switch them to CASCADE /
-- SET NULL so the deleteAccount flow can hard-delete in one transaction.

-- ai_insights: user gone -> their insights gone
ALTER TABLE ai_insights
  DROP CONSTRAINT IF EXISTS ai_insights_user_id_fkey,
  ADD  CONSTRAINT ai_insights_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ai_insights.applied_to_plan_id -> user_plans: when a user's plans are
-- cascade-deleted, any insight that referenced one of those plans must
-- go too, otherwise the user_plans cascade fails the NO ACTION check
-- at statement end and the whole deletion rolls back.
ALTER TABLE ai_insights
  DROP CONSTRAINT IF EXISTS ai_insights_applied_to_plan_id_fkey,
  ADD  CONSTRAINT ai_insights_applied_to_plan_id_fkey
    FOREIGN KEY (applied_to_plan_id) REFERENCES user_plans(plan_id) ON DELETE CASCADE;

-- user_plans: user gone -> their plans gone
ALTER TABLE user_plans
  DROP CONSTRAINT IF EXISTS user_plans_user_id_fkey,
  ADD  CONSTRAINT user_plans_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- groups.created_by: group survives, creator reference goes null.
-- The actual admin role is tracked on group_members; created_by is
-- historical/attribution only and is already nullable in V6.
ALTER TABLE groups
  DROP CONSTRAINT IF EXISTS groups_created_by_fkey,
  ADD  CONSTRAINT groups_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;
