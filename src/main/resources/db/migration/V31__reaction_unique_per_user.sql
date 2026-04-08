-- V31: Change reactions unique constraint to one reaction per user per feed item
--      (supports toggle behavior: same type → remove, different type → update)
ALTER TABLE reactions DROP CONSTRAINT IF EXISTS reactions_feed_item_id_user_id_kind_key;
ALTER TABLE reactions ADD CONSTRAINT uq_reactions_feed_item_user UNIQUE (feed_item_id, user_id);
