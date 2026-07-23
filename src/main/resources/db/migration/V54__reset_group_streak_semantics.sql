-- V54: redefine groups.streak to mean consecutive ISO weeks with bronze+ card
-- Old semantics (daily session streak) are superseded by the weekly card system.

UPDATE "groups" SET streak = 0;

COMMENT ON COLUMN "groups".streak IS
    'Consecutive ISO weeks with bronze+ card. Resets on a missed week (no card earned).';
