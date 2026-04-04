-- V24: add type and reference_id to coin_transactions for categorised awards and idempotency

ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS type         VARCHAR(50);
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS reference_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_ct_user_date
  ON coin_transactions(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ct_idempotency
  ON coin_transactions(user_id, type, reference_id);
