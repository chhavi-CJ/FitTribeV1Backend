-- Add created_at to reactions table.
-- Refreshed on every INSERT and UPDATE so kind-changes register a new timestamp.
ALTER TABLE reactions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE INDEX idx_reactions_created_at ON reactions(created_at DESC);
