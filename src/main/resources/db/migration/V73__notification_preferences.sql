CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    streak_enabled BOOLEAN NOT NULL DEFAULT true,
    group_activity_enabled BOOLEAN NOT NULL DEFAULT true,
    weekly_report_enabled BOOLEAN NOT NULL DEFAULT true,
    social_enabled BOOLEAN NOT NULL DEFAULT true,
    comeback_enabled BOOLEAN NOT NULL DEFAULT true,
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT false,
    quiet_start TIME NOT NULL DEFAULT '22:00',
    quiet_end TIME NOT NULL DEFAULT '07:00',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE INDEX idx_notification_preferences_user_id ON notification_preferences(user_id);
