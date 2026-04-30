-- Rename to make clear this column tracks only purchased freezes.
-- Bonus freezes earned by hitting weekly goal live in bonus_freeze_grants.
ALTER TABLE users RENAME COLUMN streak_freeze_balance TO purchased_freeze_balance;
