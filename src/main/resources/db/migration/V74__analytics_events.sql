CREATE TABLE analytics_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_name VARCHAR(100) NOT NULL,
    event_properties JSONB DEFAULT '{}',
    session_id VARCHAR(64),
    platform VARCHAR(20),
    app_version VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_analytics_events_event_name_created_at
    ON analytics_events(event_name, created_at DESC);

CREATE INDEX idx_analytics_events_user_id_created_at
    ON analytics_events(user_id, created_at DESC);
