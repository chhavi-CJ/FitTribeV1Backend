-- V47: Single-axis rank system based on training days.
-- Replaces the old compound-threshold system (sessions + weekly_goals + coins).
-- Thresholds: ROOKIE 0-10, GRINDER 11-50, ATHLETE 51-150, LEGEND 151+
-- Rewards: GRINDER=+100, ATHLETE=+200, LEGEND=+300
-- Honest reset: all existing ranks recomputed from real training data.
-- Coin rewards are idempotent via existing (user_id, type, reference_id) unique index.

-- Step 1: Compute training days per user and recompute rank.
-- Honest reset — rank is overwritten regardless of previous value.
WITH training_days AS (
    SELECT user_id,
           COUNT(DISTINCT DATE(finished_at)) AS days
    FROM workout_sessions
    WHERE status = 'COMPLETED' AND finished_at IS NOT NULL
    GROUP BY user_id
)
UPDATE users u
SET rank = CASE
    WHEN COALESCE(td.days, 0) >= 151 THEN 'LEGEND'
    WHEN COALESCE(td.days, 0) >=  51 THEN 'ATHLETE'
    WHEN COALESCE(td.days, 0) >=  11 THEN 'GRINDER'
    ELSE                                    'ROOKIE'
END
FROM (SELECT u2.id AS user_id FROM users u2) all_users
LEFT JOIN training_days td ON td.user_id = all_users.user_id
WHERE u.id = all_users.user_id;

-- Step 2: Award coins for users landing in GRINDER/ATHLETE/LEGEND.
-- Uses (user_id, type, reference_id) uniqueness to ensure idempotency.
-- If this migration is re-run against a DB where rewards were already paid,
-- duplicate inserts are silently skipped.
-- balance_after chain is initialized simply: we assume these users have no
-- prior RANK_PROMOTION transactions (this is the first time the type is used).
-- balance_after = (previous balance_after if exists, else users.coins) + reward
-- debt_after = (previous debt_after if exists, else 0)

INSERT INTO coin_transactions (
    id, user_id, amount, direction, label, type, reference_id,
    balance_after, debt_after, delta, clamped_amount, debt_before,
    created_at
)
SELECT
    gen_random_uuid(),
    u.id,
    CASE u.rank
        WHEN 'GRINDER' THEN 100
        WHEN 'ATHLETE' THEN 200
        WHEN 'LEGEND'  THEN 300
    END AS amount,
    'CREDIT',
    'Promoted to ' || u.rank,
    'RANK_PROMOTION',
    'RANK_PROMOTION:' || u.rank AS reference_id,
    COALESCE(
        (SELECT balance_after FROM coin_transactions
         WHERE user_id = u.id ORDER BY created_at DESC LIMIT 1),
        COALESCE(u.coins, 0)
    ) + CASE u.rank
        WHEN 'GRINDER' THEN 100
        WHEN 'ATHLETE' THEN 200
        WHEN 'LEGEND'  THEN 300
    END AS balance_after,
    COALESCE(
        (SELECT debt_after FROM coin_transactions
         WHERE user_id = u.id ORDER BY created_at DESC LIMIT 1),
        0
    ) AS debt_after,
    CASE u.rank
        WHEN 'GRINDER' THEN 100
        WHEN 'ATHLETE' THEN 200
        WHEN 'LEGEND'  THEN 300
    END AS delta,
    0 AS clamped_amount,
    COALESCE(
        (SELECT debt_after FROM coin_transactions
         WHERE user_id = u.id ORDER BY created_at DESC LIMIT 1),
        0
    ) AS debt_before,
    NOW()
FROM users u
WHERE u.rank IN ('GRINDER', 'ATHLETE', 'LEGEND')
  AND NOT EXISTS (
    SELECT 1 FROM coin_transactions ct
    WHERE ct.user_id = u.id
      AND ct.type = 'RANK_PROMOTION'
      AND ct.reference_id = 'RANK_PROMOTION:' || u.rank
  );

-- Step 3: Increment users.coins by the awarded amount for each rewarded user.
UPDATE users u
SET coins = COALESCE(u.coins, 0) + CASE u.rank
    WHEN 'GRINDER' THEN 100
    WHEN 'ATHLETE' THEN 200
    WHEN 'LEGEND'  THEN 300
END
WHERE u.rank IN ('GRINDER', 'ATHLETE', 'LEGEND')
  AND EXISTS (
    SELECT 1 FROM coin_transactions ct
    WHERE ct.user_id = u.id
      AND ct.type = 'RANK_PROMOTION'
      AND ct.reference_id = 'RANK_PROMOTION:' || u.rank
      AND ct.created_at >= NOW() - INTERVAL '1 minute'
  );
