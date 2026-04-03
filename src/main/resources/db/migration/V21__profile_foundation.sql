-- V21: profile foundation — pending_weekly_goal + weight_unit

ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_weekly_goal INT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS weight_unit VARCHAR(3)
    NOT NULL DEFAULT 'KG'
    CONSTRAINT weight_unit_check CHECK (weight_unit IN ('KG', 'LBS'));
