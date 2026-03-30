-- V15: 8 additional exercises — knee push-ups, incline push-ups,
-- inverted row, dumbbell shoulder press, leg extension,
-- tricep kickback, cable crossover, pec deck
-- Uses ON CONFLICT (id) DO NOTHING — safe to re-run

INSERT INTO exercises (
  id, name, muscle_group, equipment, icon,
  is_bodyweight, steps, common_mistakes,
  coach_tip, secondary_muscles, swap_alternatives
) VALUES

('knee-push-ups', 'Knee Push-Ups', 'CHEST',
 'BODYWEIGHT', '💪', true,
 ARRAY[
   'Start on hands and knees, hands shoulder width apart',
   'Lower chest toward floor keeping body straight from knees up',
   'Push back up to start position',
   'Keep core braced throughout',
   'Do not let hips sag or rise'
 ],
 ARRAY[
   'Hips rising — body should be straight from knees to shoulders',
   'Not going low enough — chest should nearly touch floor',
   'Flaring elbows wide — keep at 45 degrees from body'
 ],
 'Knee push-ups are the perfect starting point. Master these before progressing to full push-ups. Focus on feeling your chest muscles contract.',
 ARRAY['Front delts', 'Triceps'],
 '{}'
),

('incline-push-ups', 'Incline Push-Ups', 'CHEST',
 'BODYWEIGHT', '💪', true,
 ARRAY[
   'Place hands on a raised surface like a bench or wall',
   'The higher the surface the easier the movement',
   'Lower chest toward the surface with control',
   'Push back up keeping body in a straight line',
   'Core tight throughout'
 ],
 ARRAY[
   'Hips sagging — maintain a straight plank position',
   'Surface too low before you are ready for it',
   'Not going through full range of motion'
 ],
 'Incline push-ups sit between knee push-ups and full push-ups in difficulty. Progress to a lower surface as you get stronger.',
 ARRAY['Front delts', 'Triceps', 'Core'],
 '{}'
),

('inverted-row', 'Inverted Row', 'BACK',
 'BODYWEIGHT', '⬆️', true,
 ARRAY[
   'Set a bar at waist height in a rack or use a table',
   'Hang underneath with overhand grip, body straight',
   'Pull chest up to the bar squeezing shoulder blades',
   'Lower slowly until arms are fully extended',
   'Keep body rigid like a plank throughout'
 ],
 ARRAY[
   'Hips sagging — keep body straight',
   'Not pulling all the way up to the bar',
   'Using momentum instead of controlled pull'
 ],
 'Make it easier by raising the bar higher. Make it harder by lowering it. This is a great beginner back exercise before pull-ups.',
 ARRAY['Biceps', 'Rear delts', 'Core'],
 '{}'
),

('db-shoulder-press', 'Dumbbell Shoulder Press', 'SHOULDERS',
 'DUMBBELL', '💪', false,
 ARRAY[
   'Sit on bench with back supported, dumbbells at shoulder height',
   'Palms facing forward, elbows at 90 degrees',
   'Press dumbbells up until arms are fully extended',
   'Lower slowly back to shoulder height',
   'Keep core braced and avoid arching lower back'
 ],
 ARRAY[
   'Arching lower back excessively',
   'Pressing dumbbells forward instead of straight up',
   'Using momentum to press the weight'
 ],
 'Dumbbells allow each arm to work independently fixing strength imbalances. Keep the movement controlled especially on the way down.',
 ARRAY['Triceps', 'Upper traps'],
 '{}'
),

('leg-extension', 'Leg Extension', 'LEGS',
 'MACHINE', '🦵', false,
 ARRAY[
   'Sit in machine with back flat against pad',
   'Position pad just above the ankles',
   'Extend legs until fully straight',
   'Hold for 1 second at the top',
   'Lower slowly over 3 seconds'
 ],
 ARRAY[
   'Using momentum to swing weight up',
   'Not reaching full extension at the top',
   'Dropping weight too fast on the way down'
 ],
 'Leg extensions isolate the quads directly. Keep the movement slow and controlled. This is a finishing exercise not a primary lift.',
 ARRAY['Rectus femoris'],
 '{}'
),

('tricep-kickback', 'Tricep Kickback', 'TRICEPS',
 'DUMBBELL', '💪', false,
 ARRAY[
   'Hinge forward at hips, upper arm parallel to floor',
   'Hold dumbbell with elbow bent at 90 degrees',
   'Extend forearm back until arm is fully straight',
   'Hold for 1 second at full extension',
   'Lower slowly back to 90 degrees'
 ],
 ARRAY[
   'Upper arm dropping during the movement',
   'Using momentum to swing the weight back',
   'Not reaching full extension'
 ],
 'The key is keeping your upper arm completely still. Only your forearm moves. Use a lighter weight than you think you need.',
 ARRAY['Anconeus'],
 '{}'
),

('cable-crossover', 'Cable Crossover', 'CHEST',
 'CABLE', '💪', false,
 ARRAY[
   'Set cables to shoulder height on both sides',
   'Stand in the middle, one foot forward for balance',
   'Pull handles down and together in an arc',
   'Hands should meet in front of your hips',
   'Return slowly feeling the stretch across the chest'
 ],
 ARRAY[
   'Bending elbows too much — slight bend only',
   'Using too much weight — reduces range of motion',
   'Not getting a full stretch at the start position'
 ],
 'Think of hugging a tree. The arc motion is what makes this exercise effective. Feel the squeeze when hands meet at the bottom.',
 ARRAY['Front delts', 'Triceps'],
 '{}'
),

('pec-deck', 'Pec Deck', 'CHEST',
 'MACHINE', '🏋️', false,
 ARRAY[
   'Sit with back flat against the pad',
   'Place forearms on the arm pads',
   'Bring arms together in front squeezing chest',
   'Hold for 1 second at peak contraction',
   'Return slowly until a good stretch is felt'
 ],
 ARRAY[
   'Going too far back on the return — shoulder strain',
   'Not squeezing at the peak of the movement',
   'Using momentum to slam the pads together'
 ],
 'The machine guides the movement making this great for beginners to feel the chest working. Focus on the squeeze not the weight.',
 ARRAY['Front delts'],
 '{}'
) ON CONFLICT (id) DO NOTHING;
