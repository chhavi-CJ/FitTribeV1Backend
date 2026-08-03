-- V75: Missing performance indexes for high-traffic queries
-- These indexes optimize:
-- 1. Group feed queries (findTop30ByGroupIdOrderByCreatedAtDesc)
-- 2. PR detection filters (exercise + is_pr = TRUE)
-- 3. Notification scheduler mutual-exclusion checks (recipient + type)

-- 1. Feed items: group feed queries use findTop30ByGroupIdOrderByCreatedAtDesc
-- Without this index, queries scan all feed items instead of using group_id + date
CREATE INDEX IF NOT EXISTS idx_feed_items_group_created
  ON feed_items(group_id, created_at DESC);

-- 2. Set logs: PR detection filters by exercise + is_pr
-- Enables efficient filtering of PR events per exercise
CREATE INDEX IF NOT EXISTS idx_set_logs_exercise_pr
  ON set_logs(exercise_id, is_pr) WHERE is_pr = TRUE;

-- 3. Notifications: scheduler mutual-exclusion checks by recipient + type
-- Used by streakRiskJob, groupTrainedWithoutYouJob, comebackNudgeJob
-- to check hasReceivedPushToday(userId, type1, type2, ...)
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_type
  ON notifications(recipient_id, type, created_at DESC);
