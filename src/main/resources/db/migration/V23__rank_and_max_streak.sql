-- V23: rank and max_streak_ever on users

ALTER TABLE users
  ADD COLUMN rank VARCHAR(10) NOT NULL DEFAULT 'ROOKIE'
    CONSTRAINT rank_check CHECK (rank IN ('ROOKIE','GRINDER','ATHLETE','LEGEND')),
  ADD COLUMN max_streak_ever INT NOT NULL DEFAULT 0;
