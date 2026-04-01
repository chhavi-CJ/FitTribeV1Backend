-- V18: Tables for daily plan generation and user day status

CREATE TABLE user_day_status (
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date      DATE NOT NULL,
    status    VARCHAR(20) NOT NULL
              CHECK (status IN ('REST','TRAVELLING','BUSY','SICK')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, date)
);

CREATE INDEX idx_user_day_status_lookup
    ON user_day_status(user_id, date);

CREATE TABLE daily_plan_generated (
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date         DATE NOT NULL,
    day_type     VARCHAR(30),
    exercises    JSONB NOT NULL,
    session_note TEXT,
    cardio_suggestion JSONB,
    generated_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, date)
);

CREATE INDEX idx_daily_plan_lookup
    ON daily_plan_generated(user_id, date);
