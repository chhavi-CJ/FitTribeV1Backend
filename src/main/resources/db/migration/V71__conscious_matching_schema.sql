-- V71: Conscious Matching schema
-- Adds matching_profile table, groups.created_via, and icebreaker columns
-- on group_members. The unique constraint on group_members(group_id, user_id)
-- already exists (group_members_group_id_user_id_key) so is not re-added.

-- 1. New table: matching_profile
CREATE TABLE matching_profile (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  answer_q1             VARCHAR(64) NOT NULL,
  answer_q2             VARCHAR(64) NOT NULL,
  answer_q3             VARCHAR(64) NOT NULL,
  answer_q4             VARCHAR(64) NOT NULL,
  score_q1              INT NOT NULL,
  score_q2              INT NOT NULL,
  score_q3              INT NOT NULL,
  score_q4              INT NOT NULL,
  archetype             VARCHAR(32) NOT NULL,
  partner_gender_pref   VARCHAR(8)  NOT NULL DEFAULT 'ANY',
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT matching_profile_archetype_chk
    CHECK (archetype IN ('ANCHOR','STRIVER','RETURNER','SOCIAL_BUTTERFLY','GRINDER','SEEKER')),
  CONSTRAINT matching_profile_gender_pref_chk
    CHECK (partner_gender_pref IN ('SAME','ANY'))
);

CREATE INDEX idx_matching_profile_archetype ON matching_profile(archetype);

-- 2. groups.created_via
ALTER TABLE groups
  ADD COLUMN created_via VARCHAR(16) NOT NULL DEFAULT 'INVITE';

ALTER TABLE groups
  ADD CONSTRAINT groups_created_via_chk
  CHECK (created_via IN ('INVITE','MATCHED'));

CREATE INDEX idx_groups_created_via ON groups(created_via);

-- 3. Icebreaker columns on group_members (all nullable, filled after group forms)
ALTER TABLE group_members
  ADD COLUMN intro_reason TEXT,
  ADD COLUMN intro_goal   TEXT,
  ADD COLUMN intro_fact   TEXT;
