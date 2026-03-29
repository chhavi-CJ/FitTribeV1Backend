-- V6: groups + group_members
-- Note: "groups" is a reserved keyword in PostgreSQL 11+; quoted throughout.

CREATE TABLE "groups" (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    icon         VARCHAR(10),
    color        VARCHAR(20),
    invite_code  VARCHAR(20)  UNIQUE,
    streak       INT          DEFAULT 0,
    weekly_goal  INT          DEFAULT 4,
    created_by   UUID         REFERENCES users(id),
    created_at   TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE group_members (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID         NOT NULL REFERENCES "groups"(id) ON DELETE CASCADE,
    user_id    UUID         NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    role       VARCHAR(20)  DEFAULT 'MEMBER',
    joined_at  TIMESTAMPTZ  DEFAULT NOW(),
    UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_group_id ON group_members(group_id);
CREATE INDEX idx_group_members_user_id  ON group_members(user_id);
CREATE UNIQUE INDEX idx_groups_invite_code ON "groups"(invite_code);
