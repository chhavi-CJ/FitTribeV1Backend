-- Waitlist signups from the marketing site (wynners.in).
-- Joined to users table later (on phone) when the person installs the app.

-- Atomic, race-free position assignment: each INSERT gets the next value without a count() read.
CREATE SEQUENCE waitlist_position_seq START 101;

CREATE TABLE waitlist_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL,
    phone TEXT NOT NULL,          -- 10-digit Indian mobile, no +91 prefix
    referral_code TEXT NOT NULL UNIQUE,   -- e.g. "W9K2X7"
    referred_by_code TEXT NULL,   -- if they came via /join?ref=XXXXX
    position INT NOT NULL DEFAULT nextval('waitlist_position_seq'),
    start_position INT NOT NULL DEFAULT 0,  -- set by trigger on insert; never changes after
    referral_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT waitlist_position_unique UNIQUE (position)
);

-- One entry per email; one entry per phone.
-- If someone submits the same email or phone twice, we return the existing entry.
CREATE UNIQUE INDEX waitlist_entries_email_lower_idx
    ON waitlist_entries (LOWER(email));
CREATE UNIQUE INDEX waitlist_entries_phone_idx
    ON waitlist_entries (phone);

-- Fast referral lookups
CREATE INDEX waitlist_entries_referral_code_idx
    ON waitlist_entries (referral_code);
CREATE INDEX waitlist_entries_referred_by_idx
    ON waitlist_entries (referred_by_code);

-- Copies the DB-assigned position into start_position on every new row.
-- In PG, DEFAULT values are applied before BEFORE triggers, so NEW.position
-- already holds the sequence value when this fires.
CREATE OR REPLACE FUNCTION waitlist_entries_set_start_position()
RETURNS TRIGGER AS $$
BEGIN
    NEW.start_position = NEW.position;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER waitlist_entries_start_position_trigger
    BEFORE INSERT ON waitlist_entries
    FOR EACH ROW
    EXECUTE FUNCTION waitlist_entries_set_start_position();

-- updated_at trigger
CREATE OR REPLACE FUNCTION waitlist_entries_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER waitlist_entries_updated_at
    BEFORE UPDATE ON waitlist_entries
    FOR EACH ROW
    EXECUTE FUNCTION waitlist_entries_update_timestamp();
