-- Reference table: target 1RM for each exercise, expressed as a multiple
-- of the user's bodyweight. Bodyweight exercises use rep_target instead;
-- is_bodyweight is derived via JOIN to exercises.is_bodyweight (not
-- duplicated here to avoid sync risk).
--
-- Only BEGINNER rows are seeded for v1. INTERMEDIATE / ADVANCED added
-- in a future migration when real user data is available.
--
-- Exercises that map to CORE (plank, crunches) are excluded — they do
-- not participate in the 6-muscle TrendsMuscle scoring model.
CREATE TABLE exercise_strength_targets (
    exercise_id     VARCHAR(50)   NOT NULL REFERENCES exercises(id),
    gender          VARCHAR(10)   NOT NULL,   -- MALE | FEMALE
    fitness_level   VARCHAR(20)   NOT NULL,   -- BEGINNER | INTERMEDIATE | ADVANCED
    -- For weighted exercises: target1RM = multiplier × user.weightKg.
    -- NULL for bodyweight exercises (use rep_target instead).
    multiplier      NUMERIC(4,2),
    -- For bodyweight exercises: the target rep count at bodyweight.
    -- targetRM is derived as: bodyweightKg × (1 + repTarget / 30.0).
    -- NULL for weighted exercises.
    rep_target      INT,
    -- formula_version: must stay in lockstep with strength_score_history.formula_version.
    -- Bump both columns simultaneously when the scoring formula changes.
    formula_version INT           NOT NULL DEFAULT 1,
    CONSTRAINT pk_exercise_strength_targets
        PRIMARY KEY (exercise_id, gender, fitness_level)
);

-- ── BEGINNER × MALE ───────────────────────────────────────────────────────
-- Multipliers from ExRx / Symmetric Strength published beginner standards.
-- Female targets are approximately 65–70 % of male equivalents.
INSERT INTO exercise_strength_targets
    (exercise_id, gender, fitness_level, multiplier, rep_target)
VALUES
-- CHEST
('bench-press',       'MALE', 'BEGINNER', 1.00, NULL),
('incline-db-press',  'MALE', 'BEGINNER', 0.65, NULL),
('cable-flyes',       'MALE', 'BEGINNER', 0.30, NULL),
('push-ups',          'MALE', 'BEGINNER', NULL, 10  ),
-- BACK
('pull-ups',          'MALE', 'BEGINNER', NULL, 5   ),
('barbell-row',       'MALE', 'BEGINNER', 0.75, NULL),
('lat-pulldown',      'MALE', 'BEGINNER', 0.75, NULL),
('romanian-deadlift', 'MALE', 'BEGINNER', 1.00, NULL),
-- LEGS
('squat',             'MALE', 'BEGINNER', 1.25, NULL),
('leg-press',         'MALE', 'BEGINNER', 1.75, NULL),
('leg-curl',          'MALE', 'BEGINNER', 0.45, NULL),
-- SHOULDERS
('shoulder-press',    'MALE', 'BEGINNER', 0.50, NULL),
('overhead-press',    'MALE', 'BEGINNER', 0.55, NULL),
('lateral-raises',    'MALE', 'BEGINNER', 0.20, NULL),
-- BICEPS
('bicep-curl',        'MALE', 'BEGINNER', 0.35, NULL),
-- TRICEPS
('tricep-pushdowns',  'MALE', 'BEGINNER', 0.35, NULL),
('dips',              'MALE', 'BEGINNER', NULL, 8   );

-- ── BEGINNER × FEMALE ────────────────────────────────────────────────────
INSERT INTO exercise_strength_targets
    (exercise_id, gender, fitness_level, multiplier, rep_target)
VALUES
-- CHEST
('bench-press',       'FEMALE', 'BEGINNER', 0.65, NULL),
('incline-db-press',  'FEMALE', 'BEGINNER', 0.42, NULL),
('cable-flyes',       'FEMALE', 'BEGINNER', 0.20, NULL),
('push-ups',          'FEMALE', 'BEGINNER', NULL, 8   ),
-- BACK
('pull-ups',          'FEMALE', 'BEGINNER', NULL, 3   ),
('barbell-row',       'FEMALE', 'BEGINNER', 0.50, NULL),
('lat-pulldown',      'FEMALE', 'BEGINNER', 0.50, NULL),
('romanian-deadlift', 'FEMALE', 'BEGINNER', 0.65, NULL),
-- LEGS
('squat',             'FEMALE', 'BEGINNER', 0.85, NULL),
('leg-press',         'FEMALE', 'BEGINNER', 1.25, NULL),
('leg-curl',          'FEMALE', 'BEGINNER', 0.30, NULL),
-- SHOULDERS
('shoulder-press',    'FEMALE', 'BEGINNER', 0.33, NULL),
('overhead-press',    'FEMALE', 'BEGINNER', 0.35, NULL),
('lateral-raises',    'FEMALE', 'BEGINNER', 0.13, NULL),
-- BICEPS
('bicep-curl',        'FEMALE', 'BEGINNER', 0.23, NULL),
-- TRICEPS
('tricep-pushdowns',  'FEMALE', 'BEGINNER', 0.23, NULL),
('dips',              'FEMALE', 'BEGINNER', NULL, 5   );
