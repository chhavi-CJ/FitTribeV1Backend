-- One row per user. Holds the latest computed progress payload as opaque
-- JSONB so the shape can evolve without a schema migration. Java owns the
-- internal structure via schema_version.
CREATE TABLE user_progress_snapshot (
    user_id        UUID        NOT NULL PRIMARY KEY
                               REFERENCES users(id) ON DELETE CASCADE,
    data           JSONB       NOT NULL DEFAULT '{}',
    -- schema_version tracks the Java-side payload shape.
    -- Increment when the data JSONB structure changes incompatibly.
    schema_version INT         NOT NULL DEFAULT 1,
    computed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
