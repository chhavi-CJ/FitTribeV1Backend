-- V38: Seed missing exercise strength targets
-- Adds targets for 8 exercises appearing in real workout sessions.
-- Excludes CORE/FULL_BODY exercises (mountain-climbers, etc.) which map to null.
-- Uses ON CONFLICT to safely re-run without duplicate errors.

-- ── BEGINNER × MALE ───────────────────────────────────────────────────────
INSERT INTO exercise_strength_targets
    (exercise_id, gender, fitness_level, multiplier, rep_target)
VALUES
-- BACK
('deadlift',              'MALE', 'BEGINNER', 1.50, NULL),
('chin-ups',              'MALE', 'BEGINNER', NULL, 6   ),
('seated-cable-row',      'MALE', 'BEGINNER', 0.65, NULL),
('dumbbell-row',          'MALE', 'BEGINNER', 0.45, NULL),
('face-pulls',            'MALE', 'BEGINNER', 0.20, NULL),
-- CHEST
('smith-machine-bench',   'MALE', 'BEGINNER', 1.00, NULL),
-- LEGS (CALVES map to LEGS in TrendsMuscleMapper)
('standing-calf-raises',  'MALE', 'BEGINNER', NULL, 15  ),
('single-leg-calf-raise', 'MALE', 'BEGINNER', NULL, 8   )
ON CONFLICT (exercise_id, gender, fitness_level) DO NOTHING;

-- ── BEGINNER × FEMALE ────────────────────────────────────────────────────
INSERT INTO exercise_strength_targets
    (exercise_id, gender, fitness_level, multiplier, rep_target)
VALUES
-- BACK
('deadlift',              'FEMALE', 'BEGINNER', 1.00, NULL),
('chin-ups',              'FEMALE', 'BEGINNER', NULL, 3   ),
('seated-cable-row',      'FEMALE', 'BEGINNER', 0.42, NULL),
('dumbbell-row',          'FEMALE', 'BEGINNER', 0.30, NULL),
('face-pulls',            'FEMALE', 'BEGINNER', 0.13, NULL),
-- CHEST
('smith-machine-bench',   'FEMALE', 'BEGINNER', 0.65, NULL),
-- LEGS (CALVES map to LEGS in TrendsMuscleMapper)
('standing-calf-raises',  'FEMALE', 'BEGINNER', NULL, 12  ),
('single-leg-calf-raise', 'FEMALE', 'BEGINNER', NULL, 6   )
ON CONFLICT (exercise_id, gender, fitness_level) DO NOTHING;
