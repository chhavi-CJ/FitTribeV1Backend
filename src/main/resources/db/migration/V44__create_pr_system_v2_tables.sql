-- V44: PR System V2 schema foundation
-- Creates four tables: user_exercise_bests, pr_events (RANGE partitioned),
-- weekly_pr_counts, and extends coin_transactions with ledger semantics.
--
-- Postgres 18 supports declarative RANGE partitioning. Initial partitions
-- created for 2026-04 through 2026-07, plus DEFAULT partition for safety.

-- ──────────────────────────────────────────────────────────────────────────
-- COIN TRANSACTIONS LEDGER EXTENSION (from V7 base, V24 extension)
-- ──────────────────────────────────────────────────────────────────────────

-- Add new columns for ledger semantics (Phase 2+ will populate these).
-- Existing 72 rows have NULL initially; backfill will compute balance_after.
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS delta INT;
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS reason VARCHAR(50);
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS reference_type VARCHAR(50);
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS balance_after INT;
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS debt_before INT DEFAULT 0;
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS debt_after INT DEFAULT 0;
ALTER TABLE coin_transactions ADD COLUMN IF NOT EXISTS clamped_amount INT DEFAULT 0;

-- Backfill balance_after using a window function, O(n log n).
-- direction = 'CREDIT' adds to balance, 'DEBIT' subtracts.
-- Deterministic ordering by created_at, then id (for same-timestamp rows).
-- Rows are currently 72 total (65 CREDIT, 7 DEBIT) — no performance issue.
UPDATE coin_transactions ct
SET balance_after = sq.running_balance
FROM (
  SELECT id,
    SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END)
      OVER (PARTITION BY user_id ORDER BY created_at, id
            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
      AS running_balance
  FROM coin_transactions
) sq
WHERE ct.id = sq.id AND ct.balance_after IS NULL;

-- Enforce non-NULL after backfill.
ALTER TABLE coin_transactions ALTER COLUMN balance_after SET NOT NULL;

-- Index already exists from V24: idx_ct_user_date (user_id, created_at DESC).
-- Index for (reference_type, reference_id) lookups when events are superseded.
CREATE INDEX IF NOT EXISTS idx_ct_reference
  ON coin_transactions(reference_type, reference_id);

-- ──────────────────────────────────────────────────────────────────────────
-- USER_EXERCISE_BESTS: Mutable cache of per-user, per-exercise maxima
-- ──────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_exercise_bests (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  exercise_id VARCHAR(50) NOT NULL REFERENCES exercises(id),
  exercise_type VARCHAR(30) NOT NULL,
    -- WEIGHTED, BODYWEIGHT_UNASSISTED, BODYWEIGHT_ASSISTED, BODYWEIGHT_WEIGHTED, TIMED

  -- Weight-based signals (NULL for TIMED exercises)
  best_wt_kg DECIMAL(6,2),
  reps_at_best_wt INT,
  best_reps INT,
  wt_at_best_reps_kg DECIMAL(6,2),
  best_1rm_epley_kg DECIMAL(6,2),
  best_set_volume_kg DECIMAL(8,2),
  best_session_volume_kg DECIMAL(10,2),

  -- Timed exercises (NULL for weight-based)
  best_hold_seconds INT,

  -- Aggregate signals
  total_sessions_with_exercise INT DEFAULT 0,
  last_logged_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  PRIMARY KEY (user_id, exercise_id)
);

CREATE INDEX IF NOT EXISTS idx_ueb_user_id
  ON user_exercise_bests(user_id);

-- ──────────────────────────────────────────────────────────────────────────
-- PR_EVENTS: Append-only log of every PR event, RANGE partitioned by week_start
-- ──────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS pr_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  exercise_id VARCHAR(50) NOT NULL REFERENCES exercises(id),
  session_id UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
  set_id UUID,
    -- Nullable for session-level events (future use)

  pr_category VARCHAR(30) NOT NULL,
    -- FIRST_EVER, WEIGHT_PR, REP_PR, VOLUME_PR, MAX_ATTEMPT

  signals_met JSONB NOT NULL DEFAULT '{}',
    -- { "weight": true, "rep": false, "volume": true, "one_rm": true }

  value_payload JSONB NOT NULL DEFAULT '{}',
    -- Structured: { "delta_kg": 5, "previous_best": {...}, "new_best": {...} }

  coins_awarded INT NOT NULL DEFAULT 0,
  detector_version VARCHAR(20) NOT NULL DEFAULT 'v1.0',

  week_start DATE NOT NULL,
    -- Partition key, sealed at week end

  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  superseded_at TIMESTAMPTZ,
    -- Set when edit invalidates this event
  superseded_by UUID,
    -- References replacement event if any

  CONSTRAINT fk_superseded_by FOREIGN KEY (superseded_by) REFERENCES pr_events(id) ON DELETE SET NULL
)
PARTITION BY RANGE (week_start);

-- Initial partitions: 2026-04 through 2026-07, plus DEFAULT for safety.
CREATE TABLE pr_events_2026_04 PARTITION OF pr_events
  FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE pr_events_2026_05 PARTITION OF pr_events
  FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE pr_events_2026_06 PARTITION OF pr_events
  FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE TABLE pr_events_2026_07 PARTITION OF pr_events
  FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

CREATE TABLE pr_events_default PARTITION OF pr_events
  DEFAULT;

-- Indexes on base table; inherited by partitions.
CREATE INDEX IF NOT EXISTS idx_pr_events_user_week
  ON pr_events(user_id, week_start);

CREATE INDEX IF NOT EXISTS idx_pr_events_session_id
  ON pr_events(session_id);

CREATE INDEX IF NOT EXISTS idx_pr_events_user_exercise_date
  ON pr_events(user_id, exercise_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pr_events_superseded
  ON pr_events(superseded_at) WHERE superseded_at IS NULL;

-- ──────────────────────────────────────────────────────────────────────────
-- WEEKLY_PR_COUNTS: Pre-aggregated counters per user per week
-- ──────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS weekly_pr_counts (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  week_start DATE NOT NULL,

  first_ever_count INT DEFAULT 0,
  pr_count INT DEFAULT 0,
    -- Excludes first_ever and max_attempt
  max_attempt_count INT DEFAULT 0,
  total_coins_awarded INT DEFAULT 0,

  sealed_at TIMESTAMPTZ,
    -- Set by Sunday cron at week end; read-only after
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  PRIMARY KEY (user_id, week_start)
);

CREATE INDEX IF NOT EXISTS idx_wpc_user_week
  ON weekly_pr_counts(user_id, week_start DESC);

-- ──────────────────────────────────────────────────────────────────────────
-- Verification: Backfill count
-- ──────────────────────────────────────────────────────────────────────────
-- After migration: SELECT COUNT(*) FROM coin_transactions WHERE balance_after IS NOT NULL;
-- Expected: 72 rows (100% of original table).
--
-- SELECT COUNT(*), COUNT(DISTINCT user_id) FROM coin_transactions WHERE balance_after IS NOT NULL;
-- Expected: 72 rows, ≥1 users (most users are likely just one or two with coins at MVP stage).
