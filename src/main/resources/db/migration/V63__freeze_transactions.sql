CREATE TABLE freeze_transactions (
    id           BIGSERIAL                    PRIMARY KEY,
    user_id      UUID                         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type   VARCHAR(20)                  NOT NULL CHECK (event_type IN ('PURCHASED','USED_AUTO_APPLY','BONUS_EARNED','BONUS_USED','BONUS_EXPIRED')),
    amount       INTEGER                      NOT NULL,
    occurred_at  TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW(),
    metadata     JSONB,
    created_at   TIMESTAMP WITH TIME ZONE     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_freeze_tx_user_recent
    ON freeze_transactions(user_id, occurred_at DESC);
