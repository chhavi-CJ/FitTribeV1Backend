-- V70: backfill weekly_goal=0 snapshots poisoned by the onMemberJoinedGroup
-- seeding bug, and recompute the affected weeks' target/tier.
--
-- Bug: onMemberJoinedGroup unconditionally seeded weekly_goal=0 even for
-- fresh (not-yet-frozen) weeks, so those joiners were never counted into
-- target_sessions and showed 0 in the member breakdown. The Java fix
-- (resolveWeeklyGoal branch) stops new poisoning; this migration repairs
-- existing rows.
--
-- Scope rules:
--  * Only snapshots with weekly_goal=0 AND joined_this_week=true.
--  * Skip any week whose group_weekly_progress row is locked_at IS NOT NULL
--    — those weeks minted GroupWeeklyCards and must stay frozen for
--    historical integrity.
--  * Recompute target_sessions as SUM(active snapshot.weekly_goal) — the
--    same model onMemberLeftGroup assumes (snapshot is source of truth for
--    target accounting). sessions_logged is NOT touched (never affected by
--    the bug; maintained by incrementForGroup).
--  * Tier thresholds use INTEGER division to match Java int math in
--    GroupProgressService.recomputeTier: pct>=100 GOLD, >=85 SILVER,
--    >=70 BRONZE, else NONE; overachiever = pct>100.
--
-- Single atomic data-modifying CTE: repair + recompute in one statement,
-- naturally idempotent (re-running finds no weekly_goal=0 joined rows).

WITH repaired AS (
    UPDATE group_member_goal_snapshot s
    SET weekly_goal = COALESCE(u.weekly_goal, 4),
        updated_at  = now()
    FROM users u
    WHERE s.user_id = u.id
      AND s.weekly_goal = 0
      AND s.joined_this_week = true
      AND NOT EXISTS (
            SELECT 1
            FROM group_weekly_progress p
            WHERE p.group_id = s.group_id
              AND p.iso_year = s.iso_year
              AND p.iso_week = s.iso_week
              AND p.locked_at IS NOT NULL
      )
    RETURNING s.group_id, s.iso_year, s.iso_week
),
affected AS (
    SELECT DISTINCT group_id, iso_year, iso_week
    FROM repaired
),
agg AS (
    SELECT a.group_id,
           a.iso_year,
           a.iso_week,
           COALESCE(SUM(s.weekly_goal) FILTER (WHERE s.is_active), 0) AS new_target
    FROM affected a
    JOIN group_member_goal_snapshot s
      ON s.group_id = a.group_id
     AND s.iso_year = a.iso_year
     AND s.iso_week = a.iso_week
    GROUP BY a.group_id, a.iso_year, a.iso_week
)
UPDATE group_weekly_progress p
SET target_sessions = a.new_target,
    current_tier = CASE
        WHEN a.new_target = 0 THEN 'NONE'
        WHEN (p.sessions_logged * 100) / a.new_target >= 100 THEN 'GOLD'
        WHEN (p.sessions_logged * 100) / a.new_target >=  85 THEN 'SILVER'
        WHEN (p.sessions_logged * 100) / a.new_target >=  70 THEN 'BRONZE'
        ELSE 'NONE'
    END,
    overachiever = (a.new_target > 0
                    AND (p.sessions_logged * 100) / a.new_target > 100),
    updated_at = now()
FROM agg a
WHERE p.group_id = a.group_id
  AND p.iso_year = a.iso_year
  AND p.iso_week = a.iso_week
  AND p.locked_at IS NULL;
