-- V17: Seed all 7 day-count x 3 fitness-level combinations
-- ON CONFLICT DO NOTHING — safe to re-run

-- ── 1 DAY ───────────────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (1,'BEGINNER',    1,'Full Body','full_body',ARRAY['Chest','Back','Shoulders','Legs','Arms','Core'],true,'One session covers everything — compounds first, core last.','brisk_walk',10,55),
  (1,'INTERMEDIATE',1,'Full Body','full_body',ARRAY['Chest','Back','Shoulders','Legs','Arms','Core'],true,'One session covers everything — compounds first, core last.','brisk_walk',10,55),
  (1,'ADVANCED',    1,'Full Body','full_body',ARRAY['Chest','Back','Shoulders','Legs','Arms','Core'],true,'One session covers everything — higher volume, compounds first, core last.','brisk_walk',10,60)
ON CONFLICT DO NOTHING;

-- ── 2 DAYS ──────────────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (2,'BEGINNER',    1,'Full Body A','full_body',ARRAY['Chest','Back','Quads','Core'],         true,'Compound push and pull with quad emphasis. Sets the foundation for Day B.','brisk_walk',10,50),
  (2,'BEGINNER',    2,'Full Body B','full_body',ARRAY['Shoulders','Back','Hamstrings','Arms'],true,'Shoulder and posterior chain focus — balances the pressing from Day A.','incline_walk',10,50),
  (2,'INTERMEDIATE',1,'Full Body A','full_body',ARRAY['Chest','Back','Quads','Core'],         true,'Compound push and pull with quad emphasis. Sets the foundation for Day B.','brisk_walk',10,50),
  (2,'INTERMEDIATE',2,'Full Body B','full_body',ARRAY['Shoulders','Back','Hamstrings','Arms'],true,'Shoulder and posterior chain focus — balances the pressing from Day A.','incline_walk',10,50),
  (2,'ADVANCED',    1,'Full Body A','full_body',ARRAY['Chest','Back','Quads','Core'],         true,'Heavy compound push and pull — higher sets per exercise than lower levels.','brisk_walk',10,55),
  (2,'ADVANCED',    2,'Full Body B','full_body',ARRAY['Shoulders','Back','Hamstrings','Arms'],true,'Posterior chain and shoulder volume. Rear delts often neglected — prioritise.','incline_walk',10,55)
ON CONFLICT DO NOTHING;

-- ── 3 DAYS BEGINNER (Upper A / Lower / Upper B) ──────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (3,'BEGINNER',1,'Upper A','upper_a',ARRAY['Chest','Shoulders','Triceps'],        false,'Push compound movements first when CNS is fresh.','brisk_walk',10,45),
  (3,'BEGINNER',2,'Lower',  'lower',  ARRAY['Quads','Hamstrings','Glutes'],        false,'Biggest muscle groups — squat and hinge movements build the most mass.','incline_walk',10,50),
  (3,'BEGINNER',3,'Upper B','upper_b',ARRAY['Back','Biceps','Rear Delts'],         true,'Pull focus balances Day 1 pressing. Core finisher rounds off the week.',NULL,NULL,45)
ON CONFLICT DO NOTHING;

-- ── 3 DAYS INTERMEDIATE (Push / Pull / Legs) ─────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (3,'INTERMEDIATE',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],false,'Compound chest and shoulder pressing. Heaviest movements first.','brisk_walk',10,45),
  (3,'INTERMEDIATE',2,'Pull', 'pull_a',ARRAY['Back','Rear Delts','Biceps'],   true,'Back thickness and width with bicep isolation. Core finisher.',NULL,NULL,45),
  (3,'INTERMEDIATE',3,'Legs', 'legs',  ARRAY['Quads','Hamstrings','Glutes'],  false,'Biggest muscle groups trained last — most recovery time before next hit.','incline_walk',10,50)
ON CONFLICT DO NOTHING;

-- ── 3 DAYS ADVANCED (Push / Pull / Legs) ─────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (3,'ADVANCED',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],         false,'Higher volume push — 2 chest compounds minimum, multiple shoulder angles.','brisk_walk',10,55),
  (3,'ADVANCED',2,'Pull', 'pull_a',ARRAY['Back','Rear Delts','Biceps'],             true,'Heavy row first, then vertical pull. Full rear delt development is mandatory.',NULL,NULL,55),
  (3,'ADVANCED',3,'Legs', 'legs',  ARRAY['Quads','Hamstrings','Glutes','Calves'],   false,'Full leg session including calves. Volume must be 16-20 sets total.','incline_walk',10,60)
ON CONFLICT DO NOTHING;

-- ── 4 DAYS BEGINNER (Upper A / Lower A / Upper B / Lower B) ──────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (4,'BEGINNER',1,'Upper A','upper_a',ARRAY['Chest','Shoulders','Triceps'],    false,'Push focused upper. Bench press and shoulder press as primary compounds.','brisk_walk',10,45),
  (4,'BEGINNER',2,'Lower A','lower_a',ARRAY['Quads','Hamstrings','Glutes'],    false,'Squat and hinge pattern. 48hr before next lower session.','incline_walk',10,50),
  (4,'BEGINNER',3,'Upper B','upper_b',ARRAY['Back','Rear Delts','Biceps'],     true,'Pull focused upper. Each upper muscle now at 2x per week frequency.',NULL,NULL,45),
  (4,'BEGINNER',4,'Lower B','lower_b',ARRAY['Quads','Glutes','Calves','Core'], true,'Second lower with glute and calf emphasis. Core finisher closes the week.','incline_walk',10,50)
ON CONFLICT DO NOTHING;

-- ── 4 DAYS INTERMEDIATE (Upper A / Lower A / Upper B / Lower B) ──────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (4,'INTERMEDIATE',1,'Upper A','upper_a',ARRAY['Chest','Shoulders','Triceps'],          false,'Strength focus — lower reps on bench and OHP. Foundation of the split.','brisk_walk',10,50),
  (4,'INTERMEDIATE',2,'Lower A','lower_a',ARRAY['Quads','Hamstrings','Glutes'],          false,'Squat and Romanian deadlift as primary movers. 48hr+ recovery before next.','incline_walk',10,55),
  (4,'INTERMEDIATE',3,'Upper B','upper_b',ARRAY['Back','Rear Delts','Biceps','Shoulders'],true,'Hypertrophy focus — higher reps. Each upper muscle at 2x this week.',NULL,NULL,50),
  (4,'INTERMEDIATE',4,'Lower B','lower_b',ARRAY['Quads','Glutes','Calves','Core'],        true,'Volume lower. Calf and core isolation rounds off the training week.','incline_walk',10,55)
ON CONFLICT DO NOTHING;

-- ── 4 DAYS ADVANCED (Push A / Pull A / Legs / Pull B) ────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (4,'ADVANCED',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],           false,'Heavy compound push — this is a strength day.','brisk_walk',10,55),
  (4,'ADVANCED',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],              true,'Back thickness priority. Heavy rows first. 48hr gap before Pull B.',NULL,NULL,55),
  (4,'ADVANCED',3,'Legs',  'legs',  ARRAY['Quads','Hamstrings','Glutes','Calves'],    false,'Full leg session. Back gets 2x — legs at 1x is the 4-day trade-off.','incline_walk',10,60),
  (4,'ADVANCED',4,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'], true,'Back width priority — pulldowns and pull-ups. Core finisher. 48hr after Pull A.',NULL,NULL,55)
ON CONFLICT DO NOTHING;

-- ── 5 DAYS BEGINNER ──────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (5,'BEGINNER',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],             false,'First push. Chest compounds first, tricep isolation last.','brisk_walk',10,45),
  (5,'BEGINNER',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],                true,'First pull. Row before pulldown for back thickness. Core finisher.',NULL,NULL,45),
  (5,'BEGINNER',3,'Legs A','legs_a',ARRAY['Quads','Hamstrings','Glutes'],              false,'Only one leg day at 5 days — make it count with high volume.','incline_walk',10,50),
  (5,'BEGINNER',4,'Push B','push_b',ARRAY['Upper Chest','Shoulders','Triceps'],         false,'Incline emphasis. 72hr from Push A — ideal chest recovery window.','brisk_walk',10,45),
  (5,'BEGINNER',5,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'],   true,'Width focus. Pulldowns and pull-ups. Core finisher closes the week.',NULL,NULL,45)
ON CONFLICT DO NOTHING;

-- ── 5 DAYS INTERMEDIATE ───────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (5,'INTERMEDIATE',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],         false,'First push. Chest compounds first, tricep isolation last.','brisk_walk',10,50),
  (5,'INTERMEDIATE',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],            true,'First pull. Row before pulldown for back thickness. Core finisher.',NULL,NULL,50),
  (5,'INTERMEDIATE',3,'Legs A','legs_a',ARRAY['Quads','Hamstrings','Glutes'],          false,'Only one leg day at 5 days — volume must compensate.','incline_walk',10,55),
  (5,'INTERMEDIATE',4,'Push B','push_b',ARRAY['Upper Chest','Shoulders','Triceps'],     false,'Incline emphasis. 72hr from Push A — optimal chest growth window.','brisk_walk',10,50),
  (5,'INTERMEDIATE',5,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'],true,'Width focus. Pulldowns and pull-ups. Core finisher closes the week.',NULL,NULL,50)
ON CONFLICT DO NOTHING;

-- ── 5 DAYS ADVANCED ───────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (5,'ADVANCED',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],             false,'Heavy push. 4 sets per compound minimum.','brisk_walk',10,55),
  (5,'ADVANCED',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],                true,'Thickness pull with heavier loads. Core finisher.',NULL,NULL,55),
  (5,'ADVANCED',3,'Legs A','legs_a',ARRAY['Quads','Hamstrings','Glutes','Calves'],      false,'Full leg session including calves. Only one leg day — volume must be high.','incline_walk',10,60),
  (5,'ADVANCED',4,'Push B','push_b',ARRAY['Upper Chest','Shoulders','Triceps'],         false,'Volume push. More isolation angles than Push A. 72hr from Push A.','brisk_walk',10,55),
  (5,'ADVANCED',5,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'],   true,'Width focus. Pull-ups and pulldowns. Advanced back needs 2x to develop fully.',NULL,NULL,55)
ON CONFLICT DO NOTHING;

-- ── 6 DAYS BEGINNER ──────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (6,'BEGINNER',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],            false,'Heavy compound push. Bench press sets the tone for the week.','brisk_walk',10,45),
  (6,'BEGINNER',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],               true,'Back thickness. Row before pulldown. 72hr to Push B.',NULL,NULL,45),
  (6,'BEGINNER',3,'Legs A','legs_a',ARRAY['Quads','Hamstrings','Glutes'],             false,'First leg session. Squat and hinge focus. 72hr before Legs B.','incline_walk',10,50),
  (6,'BEGINNER',4,'Push B','push_b',ARRAY['Upper Chest','Shoulders','Triceps'],        false,'Incline and shoulder emphasis — different angle from Push A.','brisk_walk',10,45),
  (6,'BEGINNER',5,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'],  true,'Lat width focus. Pull-ups and pulldowns with core finisher.',NULL,NULL,45),
  (6,'BEGINNER',6,'Legs B','legs_b',ARRAY['Quads','Glutes','Calves','Core'],           true,'Second leg session. Glute and calf isolation. Core ends the week.','incline_walk',10,50)
ON CONFLICT DO NOTHING;

-- ── 6 DAYS INTERMEDIATE ───────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (6,'INTERMEDIATE',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],         false,'Heavy compound push. Bench press sets the tone for the week.','brisk_walk',10,50),
  (6,'INTERMEDIATE',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],            true,'Back thickness. Row before pulldown. 72hr to Push B.',NULL,NULL,50),
  (6,'INTERMEDIATE',3,'Legs A','legs_a',ARRAY['Quads','Hamstrings','Glutes'],          false,'First leg session. Squat and hinge focus. 72hr before Legs B.','incline_walk',10,55),
  (6,'INTERMEDIATE',4,'Push B','push_b',ARRAY['Upper Chest','Shoulders','Triceps'],     false,'Incline and shoulder emphasis — different angle from Push A.','brisk_walk',10,50),
  (6,'INTERMEDIATE',5,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'],true,'Lat width focus. Pull-ups and pulldowns with core finisher.',NULL,NULL,50),
  (6,'INTERMEDIATE',6,'Legs B','legs_b',ARRAY['Quads','Glutes','Calves','Core'],        true,'Second leg session. Glute and calf isolation. Core ends the week.','incline_walk',10,55)
ON CONFLICT DO NOTHING;

-- ── 6 DAYS ADVANCED ───────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (6,'ADVANCED',1,'Push A','push_a',ARRAY['Chest','Front Delts','Triceps'],             false,'Heavy push — 4-5 sets per compound. Volume comes from Push B.','brisk_walk',10,55),
  (6,'ADVANCED',2,'Pull A','pull_a',ARRAY['Back','Rear Delts','Biceps'],                true,'Thickness pull with heavier loads. 72hr gap to Pull B maintained.',NULL,NULL,55),
  (6,'ADVANCED',3,'Legs A','legs_a',ARRAY['Quads','Hamstrings','Glutes'],              false,'Strength focus — maximum effort squat and RDL. 72hr before Legs B.','incline_walk',10,60),
  (6,'ADVANCED',4,'Push B','push_b',ARRAY['Upper Chest','Shoulders','Triceps'],         false,'Volume push. More isolation angles. 72hr from Push A.','brisk_walk',10,55),
  (6,'ADVANCED',5,'Pull B','pull_b',ARRAY['Back Width','Rear Delts','Biceps','Core'],   true,'Width and detail work. Advanced back needs 2x frequency to develop.',NULL,NULL,55),
  (6,'ADVANCED',6,'Legs B','legs_b',ARRAY['Quads','Glutes','Calves','Core'],            true,'Volume leg session. Isolation and calves for complete development.','incline_walk',10,60)
ON CONFLICT DO NOTHING;

-- ── 7 DAYS BEGINNER ──────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (7,'BEGINNER',1,'Push A',      'push_a', ARRAY['Chest','Front Delts','Triceps'],           false,'First push. Compounds first, CNS is fresh at the start of the week.','brisk_walk',10,45),
  (7,'BEGINNER',2,'Pull A',      'pull_a', ARRAY['Back','Rear Delts','Biceps'],              true,'Back and bicep session. 4-day gap before Pull B.',NULL,NULL,45),
  (7,'BEGINNER',3,'Legs A',      'legs_a', ARRAY['Quads','Hamstrings','Glutes'],            false,'First leg session. 4-day gap before Legs B allows full recovery.','incline_walk',10,50),
  (7,'BEGINNER',4,'Active Rest', 'rest',   ARRAY['Mobility','Recovery'],                     false,'Light walk and stretching only. No weight training. Essential buffer.','light_walk',20,30),
  (7,'BEGINNER',5,'Push B',      'push_b', ARRAY['Upper Chest','Shoulders','Triceps'],       false,'Second push. 4-day gap from Push A is optimal recovery.','brisk_walk',10,45),
  (7,'BEGINNER',6,'Pull B',      'pull_b', ARRAY['Back Width','Rear Delts','Biceps','Core'], true,'Width focus pull. Core finisher. 4-day gap from Pull A.',NULL,NULL,45),
  (7,'BEGINNER',7,'Legs B',      'legs_b', ARRAY['Quads','Glutes','Calves','Core'],          true,'Second leg session. Glute and calf isolation. 4-day gap from Legs A.','incline_walk',10,50)
ON CONFLICT DO NOTHING;

-- ── 7 DAYS INTERMEDIATE ───────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (7,'INTERMEDIATE',1,'Push A',      'push_a', ARRAY['Chest','Front Delts','Triceps'],           false,'First push. Compounds first, CNS is fresh at the start of the week.','brisk_walk',10,50),
  (7,'INTERMEDIATE',2,'Pull A',      'pull_a', ARRAY['Back','Rear Delts','Biceps'],              true,'Back and bicep session. Core finisher. 4-day gap before Pull B.',NULL,NULL,50),
  (7,'INTERMEDIATE',3,'Legs A',      'legs_a', ARRAY['Quads','Hamstrings','Glutes'],            false,'First leg session. 4-day gap before Legs B allows full recovery.','incline_walk',10,55),
  (7,'INTERMEDIATE',4,'Active Rest', 'rest',   ARRAY['Mobility','Recovery'],                     false,'Light walk and stretching only. No weight training. Essential buffer.','light_walk',20,30),
  (7,'INTERMEDIATE',5,'Push B',      'push_b', ARRAY['Upper Chest','Shoulders','Triceps'],       false,'Second push. 4-day gap from Push A is optimal recovery.','brisk_walk',10,50),
  (7,'INTERMEDIATE',6,'Pull B',      'pull_b', ARRAY['Back Width','Rear Delts','Biceps','Core'], true,'Width focus pull. Core finisher. 4-day gap from Pull A.',NULL,NULL,50),
  (7,'INTERMEDIATE',7,'Legs B',      'legs_b', ARRAY['Quads','Glutes','Calves','Core'],          true,'Second leg session. Glute and calf isolation. 4-day gap from Legs A.','incline_walk',10,55)
ON CONFLICT DO NOTHING;

-- ── 7 DAYS ADVANCED ───────────────────────────────────────────────────────
INSERT INTO split_template_days
  (days_per_week, fitness_level, day_number, day_label, day_type, muscle_groups, includes_core, guidance_text, cardio_type, cardio_duration_min, estimated_mins)
VALUES
  (7,'ADVANCED',1,'Push A',      'push_a', ARRAY['Chest','Front Delts','Triceps'],           false,'Heavy push. 4 sets per compound — volume split across Push A and Push B.','brisk_walk',10,55),
  (7,'ADVANCED',2,'Pull A',      'pull_a', ARRAY['Back','Rear Delts','Biceps'],              true,'Thickness pull. Heavier rows. Core finisher. 4-day gap before Pull B.',NULL,NULL,55),
  (7,'ADVANCED',3,'Legs A',      'legs_a', ARRAY['Quads','Hamstrings','Glutes'],            false,'Strength legs. Max effort squat and RDL. 4-day gap for full recovery.','incline_walk',10,60),
  (7,'ADVANCED',4,'Active Rest', 'rest',   ARRAY['Mobility','Recovery'],                     false,'Foam rolling, mobility drills, light walk. Mandatory for 7-day training.','light_walk',25,30),
  (7,'ADVANCED',5,'Push B',      'push_b', ARRAY['Upper Chest','Shoulders','Triceps'],       false,'Volume push. Incline emphasis. 4-day gap from Push A.','brisk_walk',10,55),
  (7,'ADVANCED',6,'Pull B',      'pull_b', ARRAY['Back Width','Rear Delts','Biceps','Core'], true,'Width pull. Pull-ups and pulldowns. Advanced back needs both sessions.',NULL,NULL,55),
  (7,'ADVANCED',7,'Legs B',      'legs_b', ARRAY['Quads','Glutes','Calves','Core'],          true,'Volume legs. Isolation and calves. 4-day gap from Legs A.','incline_walk',10,60)
ON CONFLICT DO NOTHING;
