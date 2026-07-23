-- Bonus freeze tokens earned by hitting the weekly workout goal.
-- Purchased freezes live on users.purchased_freeze_balance (formerly streak_freeze_balance).
-- The active bonus balance is DERIVED:
--   SELECT COUNT(*) FROM bonus_freeze_grants
--   WHERE user_id = ? AND consumed_at IS NULL
--     AND valid_from <= NOW() AND expires_at > NOW()

CREATE TABLE bonus_freeze_grants (
    id                   BIGSERIAL                    PRIMARY KEY,
    user_id              UUID                         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    earned_at            TIMESTAMP WITH TIME ZONE     NOT NULL,
    valid_from           TIMESTAMP WITH TIME ZONE     NOT NULL,
    expires_at           TIMESTAMP WITH TIME ZONE     NOT NULL,
    consumed_at          TIMESTAMP WITH TIME ZONE,
    consumption_reason   VARCHAR(20)                  CHECK (consumption_reason IN ('AUTO_APPLY', 'EXPIRED')),
    created_at           TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW()
);

-- Supports active-balance queries (user_id + unexpired + unconsumed)
CREATE INDEX idx_bonus_freeze_grants_user_active
    ON bonus_freeze_grants(user_id, expires_at)
    WHERE consumed_at IS NULL;

-- Supports the nightly expiry cleanup cron (scan all unexpired rows globally)
CREATE INDEX idx_bonus_freeze_grants_expiry_cleanup
    ON bonus_freeze_grants(expires_at)
    WHERE consumed_at IS NULL;
