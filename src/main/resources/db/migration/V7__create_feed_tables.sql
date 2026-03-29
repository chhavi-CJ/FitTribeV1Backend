-- V7: feed_items, reactions, notifications, coin_transactions

CREATE TABLE feed_items (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID         REFERENCES "groups"(id) ON DELETE CASCADE,
    user_id    UUID         REFERENCES users(id)    ON DELETE SET NULL,
    type       VARCHAR(30),
    body       TEXT,
    created_at TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_feed_items_group_id   ON feed_items(group_id);
CREATE INDEX idx_feed_items_created_at ON feed_items(created_at DESC);

-- ──────────────────────────────────────────────────────────────────────

CREATE TABLE reactions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_item_id UUID        REFERENCES feed_items(id) ON DELETE CASCADE,
    user_id      UUID        REFERENCES users(id)      ON DELETE CASCADE,
    kind         VARCHAR(20),
    UNIQUE (feed_item_id, user_id, kind)
);

CREATE INDEX idx_reactions_feed_item_id ON reactions(feed_item_id);

-- ──────────────────────────────────────────────────────────────────────

CREATE TABLE notifications (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(50),
    title      VARCHAR(200),
    body       TEXT,
    is_read    BOOLEAN      DEFAULT FALSE,
    created_at TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id    ON notifications(user_id);
CREATE INDEX idx_notifications_is_read    ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);

-- ──────────────────────────────────────────────────────────────────────

CREATE TABLE coin_transactions (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         REFERENCES users(id) ON DELETE CASCADE,
    amount     INT          NOT NULL,
    direction  VARCHAR(10),   -- 'CREDIT' | 'DEBIT'
    label      VARCHAR(100),
    created_at TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_coin_transactions_user_id ON coin_transactions(user_id);
