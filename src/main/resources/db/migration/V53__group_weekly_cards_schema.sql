-- V53: group weekly cards — progress tracking, earned cards, member snapshots

CREATE TABLE group_weekly_progress (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID        NOT NULL REFERENCES "groups"(id) ON DELETE CASCADE,
    iso_year        INT         NOT NULL,
    iso_week        INT         NOT NULL,
    target_sessions INT         NOT NULL,
    sessions_logged INT         NOT NULL DEFAULT 0,
    current_tier    VARCHAR(10) NOT NULL DEFAULT 'NONE',
    overachiever    BOOLEAN     NOT NULL DEFAULT false,
    locked_at       TIMESTAMPTZ NULL,
    goal_metadata   JSONB       NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_id, iso_year, iso_week)
);

CREATE INDEX idx_gwp_group_week ON group_weekly_progress(group_id, iso_year DESC, iso_week DESC);


CREATE TABLE group_weekly_cards (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id             UUID        NOT NULL REFERENCES "groups"(id) ON DELETE CASCADE,
    iso_year             INT         NOT NULL,
    iso_week             INT         NOT NULL,
    final_tier           VARCHAR(10) NOT NULL,
    sessions_logged      INT         NOT NULL,
    target_sessions      INT         NOT NULL,
    final_percentage     INT         NOT NULL,
    overachiever         BOOLEAN     NOT NULL DEFAULT false,
    streak_at_lock       INT         NOT NULL DEFAULT 1,
    contributor_user_ids UUID[]      NOT NULL DEFAULT '{}',
    metadata             JSONB       NULL,
    locked_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_id, iso_year, iso_week)
);

CREATE INDEX idx_gwc_group_week ON group_weekly_cards(group_id, iso_year DESC, iso_week DESC);


CREATE TABLE group_member_goal_snapshot (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id              UUID        NOT NULL REFERENCES "groups"(id) ON DELETE CASCADE,
    user_id               UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    iso_year              INT         NOT NULL,
    iso_week              INT         NOT NULL,
    weekly_goal           INT         NOT NULL,
    sessions_contributed  INT         NOT NULL DEFAULT 0,
    is_active             BOOLEAN     NOT NULL DEFAULT true,
    joined_this_week      BOOLEAN     NOT NULL DEFAULT false,
    left_this_week        BOOLEAN     NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(group_id, user_id, iso_year, iso_week)
);

CREATE INDEX idx_gmgs_group_week ON group_member_goal_snapshot(group_id, iso_year, iso_week);
CREATE INDEX idx_gmgs_user_week  ON group_member_goal_snapshot(user_id,  iso_year, iso_week);
