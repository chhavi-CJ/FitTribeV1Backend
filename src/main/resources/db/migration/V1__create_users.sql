-- V1: users table
CREATE TABLE users (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid     VARCHAR(128) UNIQUE,
    phone            VARCHAR(20)  UNIQUE NOT NULL,
    display_name     VARCHAR(100),
    gender           VARCHAR(20),
    goal             VARCHAR(50),
    fitness_level    VARCHAR(20),
    weight_kg        DECIMAL(5,2),
    height_cm        DECIMAL(5,2),
    weekly_goal      INT          DEFAULT 4,
    coins            INT          DEFAULT 0,
    streak           INT          DEFAULT 0,
    health_conditions TEXT[],
    created_at       TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_users_firebase_uid ON users(firebase_uid);
CREATE INDEX idx_users_phone        ON users(phone);
