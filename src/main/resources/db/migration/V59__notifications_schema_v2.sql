-- V59: Transform notifications table to new schema.
-- Adds actor/feed/group/metadata columns, replaces boolean is_read with
-- read_at TIMESTAMPTZ, drops title/body, adds reaction dedup index.

-- 1. Add new columns (nullable first so we can backfill before adding constraints)
ALTER TABLE notifications
  ADD COLUMN actor_id     UUID REFERENCES users(id)      ON DELETE SET NULL,
  ADD COLUMN feed_item_id UUID REFERENCES feed_items(id) ON DELETE CASCADE,
  ADD COLUMN group_id     UUID REFERENCES groups(id)     ON DELETE CASCADE,
  ADD COLUMN metadata     JSONB NOT NULL DEFAULT '{}',
  ADD COLUMN read_at      TIMESTAMPTZ;

-- 2. Backfill read_at from is_read (use created_at as best approximation of when it was read)
UPDATE notifications SET read_at = created_at WHERE is_read = TRUE;

-- 3. Backfill metadata from title+body for existing POKE notifications
UPDATE notifications
SET metadata = jsonb_build_object(
  'title', COALESCE(title, ''),
  'body',  COALESCE(body, '')
)
WHERE title IS NOT NULL OR body IS NOT NULL;

-- 4. Rename user_id -> recipient_id (PostgreSQL updates the FK constraint automatically)
ALTER TABLE notifications RENAME COLUMN user_id TO recipient_id;

-- 5. Add NOT NULL constraints (safe: all existing rows were inserted with values)
UPDATE notifications SET type = 'UNKNOWN' WHERE type IS NULL;
ALTER TABLE notifications
  ALTER COLUMN type         SET NOT NULL,
  ALTER COLUMN recipient_id SET NOT NULL;

-- 6. Narrow type column to VARCHAR(40) and add NOT NULL to created_at
ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(40);
UPDATE notifications SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE notifications ALTER COLUMN created_at SET NOT NULL;

-- 7. Drop old columns
ALTER TABLE notifications
  DROP COLUMN is_read,
  DROP COLUMN title,
  DROP COLUMN body;

-- 8. Drop old indexes (replaced below)
DROP INDEX IF EXISTS idx_notifications_user_id;
DROP INDEX IF EXISTS idx_notifications_is_read;
DROP INDEX IF EXISTS idx_notifications_created_at;

-- 9. Create new indexes
CREATE INDEX idx_notifications_recipient_unread
  ON notifications(recipient_id, created_at DESC)
  WHERE read_at IS NULL;

CREATE INDEX idx_notifications_recipient_all
  ON notifications(recipient_id, created_at DESC);

CREATE UNIQUE INDEX uq_notifications_reaction_dedup
  ON notifications(recipient_id, actor_id, feed_item_id, type)
  WHERE type = 'REACTION';
