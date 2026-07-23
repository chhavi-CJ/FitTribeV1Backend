CREATE TABLE IF NOT EXISTS device_tokens (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        TEXT        NOT NULL,
    platform     VARCHAR(10) NOT NULL CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_device_tokens          PRIMARY KEY (id),
    CONSTRAINT uq_device_tokens_user_tok UNIQUE (user_id, token)
);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id);
