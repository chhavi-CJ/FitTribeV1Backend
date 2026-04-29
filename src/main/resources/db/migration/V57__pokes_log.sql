-- V57: pokes_log — tracks who was poked in which group on which date.
-- The unique index on (group_id, recipient_user_id, poked_date) enforces the
-- one-poke-per-recipient-per-group-per-day rate limit at the DB level.
-- poked_date is stored in IST (Asia/Kolkata) — the app's canonical timezone.

CREATE TABLE pokes_log (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID        NOT NULL REFERENCES "groups"(id) ON DELETE CASCADE,
    recipient_user_id UUID        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    poker_user_id     UUID        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    poked_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    poked_date        DATE        NOT NULL
);

CREATE UNIQUE INDEX pokes_log_daily_limit
    ON pokes_log (group_id, recipient_user_id, poked_date);
