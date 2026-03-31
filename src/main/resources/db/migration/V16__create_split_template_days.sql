-- V16: Split template days — pre-seeded weekly structure by days_per_week + fitness_level
CREATE TABLE split_template_days (
    id                  SERIAL PRIMARY KEY,
    days_per_week       INT          NOT NULL,
    fitness_level       VARCHAR(20)  NOT NULL
                        CHECK (fitness_level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    day_number          INT          NOT NULL,
    day_label           VARCHAR(50)  NOT NULL,
    day_type            VARCHAR(30)  NOT NULL,
    muscle_groups       TEXT[]       NOT NULL,
    includes_core       BOOLEAN      NOT NULL DEFAULT FALSE,
    guidance_text       TEXT,
    cardio_type         VARCHAR(30),
    cardio_duration_min INT,
    estimated_mins      INT          NOT NULL DEFAULT 45,
    UNIQUE (days_per_week, fitness_level, day_number)
);

CREATE INDEX idx_split_template_lookup
    ON split_template_days(days_per_week, fitness_level);
