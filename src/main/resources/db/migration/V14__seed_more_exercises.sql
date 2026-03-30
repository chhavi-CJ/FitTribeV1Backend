-- V14: Seed 47 additional exercises across all muscle groups
-- Uses ON CONFLICT (id) DO NOTHING — safe to re-run

-- ── CHEST ─────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'db-flat-press', 'Dumbbell Flat Press', 'CHEST', 'DUMBBELL', '💪', false,
  ARRAY['Lie flat on bench with dumbbells at chest level','Press dumbbells up until arms are fully extended','Lower slowly in 3 seconds back to chest level','Keep wrists straight throughout the movement','Feet flat on floor throughout'],
  ARRAY['Letting dumbbells drift too wide apart','Dropping the weight too fast on the way down','Not touching dumbbells at the top — reduces range'],
  'Dumbbells allow a deeper stretch than a barbell. Let them sink slightly below chest level at the bottom.',
  ARRAY['Front delts','Triceps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'smith-machine-bench', 'Smith Machine Bench Press', 'CHEST', 'MACHINE', '🏋️', false,
  ARRAY['Set bar to chest height when lying down','Lie on bench, grip bar slightly wider than shoulders','Unhook bar and lower to lower chest','Press up and rotate bar to re-rack safely','Keep feet flat and back slightly arched'],
  ARRAY['Bar path too vertical — should travel slightly back','Gripping too narrow — puts excess stress on shoulders','Not controlling the descent'],
  'The fixed bar path of a Smith machine makes it great for learning the bench press movement. Focus on feeling the chest contract.',
  ARRAY['Front delts','Triceps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'decline-bench-press', 'Decline Bench Press', 'CHEST', 'BARBELL', '🏋️', false,
  ARRAY['Set bench to 15-30 degree decline','Hook feet under pad, lie back safely','Grip bar wider than shoulder width','Lower bar to lower chest slowly','Press back up powerfully'],
  ARRAY['Too steep a decline — becomes uncomfortable','Not having a spotter for heavy weights','Bar drifting toward the stomach'],
  'Decline press targets the lower chest which is often underdeveloped. Keep the angle mild — 15-20 degrees is enough.',
  ARRAY['Triceps','Front delts'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── SHOULDERS ─────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'arnold-press', 'Arnold Press', 'SHOULDERS', 'DUMBBELL', '💪', false,
  ARRAY['Sit with dumbbells at shoulder height, palms facing you','Press up while rotating palms to face forward','At the top arms are fully extended','Reverse the rotation as you lower back down','Full rotation should happen over the full rep range'],
  ARRAY['Rushing the rotation — defeats the purpose','Not going through full range of motion','Using too heavy weight — reduces rotation quality'],
  'The rotation engages all three heads of the shoulder. Slow it down — this is not a race.',
  ARRAY['Triceps','Upper traps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'front-raises', 'Front Raises', 'SHOULDERS', 'DUMBBELL', '💪', false,
  ARRAY['Stand holding dumbbells at thigh level','Raise one or both arms to shoulder height','Keep a slight bend in the elbow throughout','Lower slowly in 2-3 seconds','Do not swing or use momentum'],
  ARRAY['Swinging the weight up with momentum','Going above shoulder height — strains the joint','Shrugging the shoulders at the top'],
  'Front raises isolate the anterior delt. Use lighter weight than you think — strict form matters far more than load here.',
  ARRAY['Upper chest','Serratus anterior'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'push-press', 'Push Press', 'SHOULDERS', 'BARBELL', '🏋️', false,
  ARRAY['Hold bar at shoulder height, slight knee bend','Dip slightly by bending knees','Drive legs up and use momentum to press bar overhead','Lock out arms fully at the top','Lower bar back to shoulders with control'],
  ARRAY['Dipping too deep — becomes a thruster','Not locking out at the top','Leaning back excessively when pressing'],
  'The leg drive allows you to move heavier than a strict press. Use it for overload — not to compensate for weakness.',
  ARRAY['Triceps','Upper traps','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'cable-lateral-raise', 'Cable Lateral Raise', 'SHOULDERS', 'CABLE', '💪', false,
  ARRAY['Set cable to ankle height on one side','Stand sideways to the cable, grab handle with far hand','Raise arm to shoulder height keeping slight elbow bend','Hold for 1 second at the top','Lower slowly in 3 seconds'],
  ARRAY['Using too much weight and swinging','Raising arm too high above shoulder','Not keeping constant tension at the bottom'],
  'Cable lateral raises maintain tension throughout the full range unlike dumbbells. The bottom portion of the lift is where cables win.',
  ARRAY['Upper traps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'reverse-flyes', 'Reverse Flyes', 'BACK', 'DUMBBELL', '💪', false,
  ARRAY['Hinge forward at hips to 45 degrees','Hold dumbbells hanging down, slight elbow bend','Raise arms out to the sides to shoulder height','Squeeze shoulder blades together at the top','Lower slowly back down'],
  ARRAY['Shrugging shoulders during the movement','Using momentum to swing weights up','Not hinging enough — becomes a lateral raise'],
  'Think about leading with your elbows and squeezing your rear delts at the top. The weight should be light enough for strict form.',
  ARRAY['Rear delts','Rhomboids','Upper traps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── TRICEPS ───────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'close-grip-bench', 'Close Grip Bench Press', 'TRICEPS', 'BARBELL', '🏋️', false,
  ARRAY['Lie on flat bench, grip bar shoulder width apart','Unrack and lower bar to lower chest','Keep elbows tucked close to your body','Press back up until arms are fully extended','Do not let elbows flare out'],
  ARRAY['Gripping too narrow — strains the wrists','Letting elbows flare wide — becomes a regular bench','Bouncing bar off chest'],
  'Shoulder width grip is ideal — not as narrow as people think. Focus on keeping elbows tucked throughout.',
  ARRAY['Chest','Front delts'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'skull-crushers', 'Skull Crushers', 'TRICEPS', 'BARBELL', '🏋️', false,
  ARRAY['Lie on bench, hold bar with narrow grip above chest','Lower bar toward forehead by bending only the elbows','Keep upper arms completely still throughout','Extend back to start by straightening elbows','Do not let elbows flare out during movement'],
  ARRAY['Moving upper arms — only forearms should move','Going too heavy — high injury risk','Lowering bar behind the head instead of toward forehead'],
  'The name says it all — control this movement. Upper arms must stay locked. Use an EZ bar if straight bar causes wrist pain.',
  ARRAY['Anconeus'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'tricep-overhead-extension', 'Tricep Overhead Extension', 'TRICEPS', 'DUMBBELL', '💪', false,
  ARRAY['Stand or sit holding one dumbbell with both hands','Raise dumbbell overhead with arms fully extended','Lower dumbbell behind head by bending elbows','Keep upper arms close to your head throughout','Extend back up to start position'],
  ARRAY['Letting elbows flare out to the sides','Moving upper arms instead of just forearms','Using too heavy weight — reduces range of motion'],
  'The overhead position stretches the long head of the tricep maximally. This is one of the best tricep exercises for growth.',
  ARRAY['Serratus anterior'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── BACK ──────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'deadlift', 'Conventional Deadlift', 'BACK', 'BARBELL', '🏋️', false,
  ARRAY['Stand with bar over mid-foot, feet hip width','Hinge and grip bar just outside your legs','Flatten your back and brace your core hard','Drive through the floor — push the earth away','Lock hips and shoulders out at the top together','Lower with control by hinging at the hips first'],
  ARRAY['Rounding the lower back — most serious risk','Bar drifting away from the body','Jerking the bar off the floor','Not locking out fully at the top'],
  'Think of the deadlift as a push not a pull. Push the floor away with your legs. The bar should drag up your shins the whole way.',
  ARRAY['Hamstrings','Glutes','Core','Traps','Forearms'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'chin-ups', 'Chin Ups', 'BACK', 'BODYWEIGHT', '⬆️', true,
  ARRAY['Hang from bar with underhand grip shoulder width','Pull yourself up until chin clears the bar','Squeeze biceps and back at the top','Lower slowly until arms are fully extended','Do not swing or use momentum'],
  ARRAY['Using momentum and kipping','Not reaching full extension at the bottom','Pulling with biceps only — engage the lats'],
  'Chin ups have more bicep involvement than pull ups. Think about pulling your elbows to your hips to maximise lat engagement.',
  ARRAY['Biceps','Rear delts'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'seated-cable-row', 'Seated Cable Row', 'BACK', 'CABLE', '💪', false,
  ARRAY['Sit at cable row machine, feet on platform','Grab handle with both hands, slight knee bend','Sit tall and pull handle to your lower stomach','Squeeze shoulder blades together at end position','Return slowly until arms are fully extended'],
  ARRAY['Rocking back and forth with torso','Shrugging shoulders during the pull','Not fully extending arms at the start of each rep'],
  'Keep your torso upright throughout. The power comes from driving your elbows back not from leaning back.',
  ARRAY['Biceps','Rear delts','Rhomboids'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'dumbbell-row', 'Dumbbell Row', 'BACK', 'DUMBBELL', '💪', false,
  ARRAY['Place one knee and hand on a bench for support','Hold dumbbell in opposite hand hanging straight down','Pull dumbbell up to your hip keeping elbow close','Squeeze your lat at the top of the movement','Lower slowly back to full extension'],
  ARRAY['Rotating the torso to lift the weight','Pulling toward the shoulder instead of the hip','Not getting a full stretch at the bottom'],
  'Think about putting your elbow in your back pocket. That cue gets the elbow path exactly right for maximum lat activation.',
  ARRAY['Biceps','Rear delts','Rhomboids'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  't-bar-row', 'T-Bar Row', 'BACK', 'BARBELL', '🏋️', false,
  ARRAY['Straddle the bar, hinge to 45 degrees','Grip the handles or the bar just below the plates','Pull bar to your chest keeping elbows close','Squeeze your back hard at the top','Lower slowly until arms are fully extended'],
  ARRAY['Too upright a torso — reduces back engagement','Using momentum to jerk the weight up','Not squeezing at the top of each rep'],
  'The T-bar row allows very heavy loading with great stability. Focus on driving elbows back and up — not just pulling the weight up.',
  ARRAY['Biceps','Rear delts','Erector spinae'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'weighted-pull-ups', 'Weighted Pull Ups', 'BACK', 'BODYWEIGHT', '⬆️', false,
  ARRAY['Attach weight belt or hold dumbbell between feet','Hang from bar with overhand grip','Pull up until chin clears the bar','Lower slowly with full control','Full dead hang at the bottom each rep'],
  ARRAY['Adding weight before mastering bodyweight pull ups','Kipping or swinging','Not reaching full extension at the bottom'],
  'Only add weight once you can do 10 clean bodyweight pull ups. Start with 5kg and build slowly.',
  ARRAY['Biceps','Rear delts','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'face-pulls', 'Face Pulls', 'BACK', 'CABLE', '💪', false,
  ARRAY['Set cable to face height with rope attachment','Grip rope with overhand grip, step back','Pull rope toward your face splitting the rope at the end','Elbows should be at shoulder height throughout','Hold for 1 second before returning'],
  ARRAY['Pulling too low — becomes a row not a face pull','Not splitting the rope at the end','Using too much weight — reduces form quality'],
  'Face pulls are one of the best exercises for shoulder health and rear delt development. Keep the weight light and focus on the squeeze.',
  ARRAY['Rear delts','Rhomboids','External rotators'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── BICEPS ────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'barbell-curl', 'Barbell Curl', 'BICEPS', 'BARBELL', '💪', false,
  ARRAY['Stand holding barbell with underhand grip','Elbows pinned to sides throughout the movement','Curl bar up toward shoulders','Squeeze biceps hard at the top','Lower slowly over 3 seconds'],
  ARRAY['Swinging the body to curl the weight up','Letting elbows drift forward','Not lowering fully — partial reps reduce growth'],
  'Your elbows should not move at all. If they drift forward, the weight is too heavy. The slow negative is where the bicep grows.',
  ARRAY['Forearms','Brachialis'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'hammer-curl', 'Hammer Curl', 'BICEPS', 'DUMBBELL', '💪', false,
  ARRAY['Stand holding dumbbells with neutral grip','Keep elbows pinned to sides','Curl dumbbells up keeping palms facing each other','Squeeze at the top','Lower slowly with control'],
  ARRAY['Rotating the wrist during the movement','Swinging the body','Going too heavy — reduces isolation'],
  'Hammer curls target the brachialis which sits under the bicep and makes your arms look thicker from the side.',
  ARRAY['Brachialis','Forearms'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'concentration-curl', 'Concentration Curl', 'BICEPS', 'DUMBBELL', '💪', false,
  ARRAY['Sit on bench, lean forward slightly','Rest upper arm against inner thigh','Curl dumbbell up toward shoulder','Squeeze at the top for 2 seconds','Lower fully until arm is straight'],
  ARRAY['Moving the upper arm off the thigh','Not fully extending at the bottom','Using a weight that requires body swing'],
  'The concentration curl is one of the best bicep isolation exercises. The peak contraction squeeze is what matters here.',
  ARRAY['Brachialis'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── LEGS ──────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'goblet-squat', 'Goblet Squat', 'LEGS', 'DUMBBELL', '🦵', false,
  ARRAY['Hold dumbbell vertically at chest height','Feet shoulder width, toes slightly out','Squat down keeping chest tall and upright','Elbows should brush inside of knees at the bottom','Drive through heels to stand back up'],
  ARRAY['Letting chest fall forward','Heels rising off the floor','Not squatting deep enough'],
  'The goblet squat teaches perfect squat mechanics. The weight in front forces your torso to stay upright naturally.',
  ARRAY['Glutes','Core','Calves'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'lunges', 'Walking Lunges', 'LEGS', 'BODYWEIGHT', '🦵', true,
  ARRAY['Stand tall, feet together','Step forward with one foot into a lunge','Lower back knee toward the floor without touching','Drive through front heel to bring feet together','Alternate legs each rep'],
  ARRAY['Front knee going past the toes','Torso leaning forward excessively','Steps too short — reduces range of motion'],
  'Keep your torso upright and take a long enough stride so your front shin stays vertical at the bottom.',
  ARRAY['Glutes','Hamstrings','Core','Calves'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'bulgarian-split-squat', 'Bulgarian Split Squat', 'LEGS', 'DUMBBELL', '🦵', false,
  ARRAY['Stand in front of a bench, hold dumbbells at sides','Place rear foot on bench behind you','Lower front leg until thigh is parallel to floor','Front knee tracks over toes','Drive through front heel to return to start'],
  ARRAY['Front foot too close to bench — knee goes forward','Torso leaning forward excessively','Rear foot too high up on bench — unstable'],
  'This is one of the hardest leg exercises. Start with bodyweight until comfortable, then add dumbbells. Your front leg does all the work.',
  ARRAY['Glutes','Hamstrings','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'hack-squat', 'Hack Squat', 'LEGS', 'MACHINE', '🦵', false,
  ARRAY['Step into machine, shoulders under pads','Feet shoulder width on the platform','Release safety and lower until thighs are parallel','Drive through full foot to press back up','Do not lock out knees at the top'],
  ARRAY['Feet too low — excessive knee stress','Locking out knees at top — joint impact','Not going to parallel depth'],
  'Higher foot position targets glutes more. Lower foot position targets quads. Start in the middle and find what works for you.',
  ARRAY['Glutes','Hamstrings','Calves'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'step-ups', 'Step Ups', 'LEGS', 'DUMBBELL', '🦵', false,
  ARRAY['Stand facing a bench or box holding dumbbells','Step up with one foot onto the platform','Drive through that heel to bring your body up','Step down with the same foot','Complete all reps on one side then switch'],
  ARRAY['Pushing off the back foot — reduces leg isolation','Box too high — compromises form','Leaning forward excessively'],
  'The stepping leg should do all the work. If you find yourself pushing off the back foot the box is too high.',
  ARRAY['Glutes','Hamstrings','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── HAMSTRINGS ────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'nordic-curl', 'Nordic Curl', 'HAMSTRINGS', 'BODYWEIGHT', '🦵', true,
  ARRAY['Kneel on the floor, have partner hold ankles or hook under something','Keeping body straight from knees to head','Lower your body toward the floor slowly','Use hands to catch yourself at the bottom','Pull yourself back up using hamstrings'],
  ARRAY['Hinging at the hips — defeats the purpose','Dropping too fast — reduces the eccentric benefit','Not keeping a straight body line'],
  'This is one of the hardest hamstring exercises and has strong injury prevention benefits. Beginners can do the eccentric only — lower down and use hands to push back up.',
  ARRAY['Glutes','Calves'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'lying-leg-curl', 'Lying Leg Curl', 'HAMSTRINGS', 'MACHINE', '🦵', false,
  ARRAY['Lie face down on the machine','Position pad just above the ankles','Curl heels toward glutes in a controlled arc','Hold for 1 second at the top','Lower slowly over 3 seconds'],
  ARRAY['Hips rising off the pad','Using momentum to swing weight up','Not getting full range of motion'],
  'The slow lowering phase is where hamstring development happens. 3 seconds down on every rep.',
  ARRAY['Calves','Glutes'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── CORE ──────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'dead-bug', 'Dead Bug', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Lie on your back, arms pointing to ceiling','Knees bent at 90 degrees, shins parallel to floor','Lower opposite arm and leg toward the floor','Keep lower back pressed into the floor throughout','Return and repeat on the other side'],
  ARRAY['Lower back arching off the floor','Moving too fast — control is everything','Holding your breath'],
  'The dead bug is one of the best core stability exercises. If your back arches, reduce the range of motion until you build more strength.',
  ARRAY['Hip flexors','Transverse abdominis'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'mountain-climbers', 'Mountain Climbers', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Start in a high plank position','Drive one knee toward your chest quickly','Switch legs in a running motion','Keep hips level throughout','Maintain a strong plank position the whole time'],
  ARRAY['Hips rising too high — piking position','Not keeping shoulders over wrists','Moving so fast that form breaks down'],
  'Mountain climbers are a core and cardio combination. Keep your hips level — if they rise, slow down.',
  ARRAY['Shoulders','Hip flexors','Chest'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'russian-twist', 'Russian Twist', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Sit with knees bent, lean back to 45 degrees','Lift feet off the floor','Rotate torso left to right touching the floor each side','Keep core braced throughout','Add a weight plate or dumbbell to increase difficulty'],
  ARRAY['Rotating arms only instead of the whole torso','Losing balance and dropping feet','Not going through full rotation range'],
  'The rotation should come from your torso not just your arms. Think about your belly button pointing left and right.',
  ARRAY['Hip flexors','Obliques'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'leg-raises', 'Leg Raises', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Lie flat on your back, legs straight','Place hands under your lower back for support','Raise legs to 90 degrees keeping them straight','Lower slowly until heels are just above the floor','Do not let heels touch the floor between reps'],
  ARRAY['Lower back arching off the floor','Bending the knees to make it easier','Dropping legs too fast'],
  'The lower back should stay pressed into the floor. If it arches, you are not ready for this weight — bend your knees slightly.',
  ARRAY['Hip flexors','Transverse abdominis'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'bicycle-crunches', 'Bicycle Crunches', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Lie on back, hands behind head lightly','Lift shoulder blades off the floor','Bring one knee to chest while rotating opposite elbow toward it','Switch sides in a pedalling motion','Keep lower back pressed into the floor'],
  ARRAY['Pulling neck with hands','Moving too fast — quality over speed','Not rotating fully to each side'],
  'Slow deliberate bicycle crunches beat fast sloppy ones every time. Focus on the rotation touching elbow to knee.',
  ARRAY['Obliques','Hip flexors'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'hanging-leg-raises', 'Hanging Leg Raises', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Hang from a pull up bar with overhand grip','Brace core and raise legs to 90 degrees','Lower slowly without swinging','For advanced: raise legs all the way to the bar','Dead stop at the bottom each rep'],
  ARRAY['Swinging the body to generate momentum','Not controlling the descent','Bending knees to compensate for weak core'],
  'Hanging leg raises are an advanced core exercise. Master lying leg raises first. If you swing, do dead hangs to build grip strength.',
  ARRAY['Hip flexors','Forearms','Lats'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── GLUTES ────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'hip-thrust', 'Barbell Hip Thrust', 'GLUTES', 'BARBELL', '🏋️', false,
  ARRAY['Sit with upper back against a bench, bar across hips','Feet flat on floor, knees bent at 90 degrees','Drive hips up until body forms a straight line','Squeeze glutes hard at the top for 2 seconds','Lower hips slowly back toward the floor'],
  ARRAY['Hyperextending the lower back at the top','Feet too far or too close — find your stance','Not squeezing glutes at the top'],
  'The hip thrust is the single best glute exercise. At the top your shins should be vertical. Use a pad on the bar for comfort.',
  ARRAY['Hamstrings','Core','Adductors'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'glute-bridge', 'Glute Bridge', 'GLUTES', 'BODYWEIGHT', '🏋️', true,
  ARRAY['Lie on your back, knees bent, feet flat on floor','Drive hips up by squeezing your glutes','Hold at the top for 2 seconds','Lower hips slowly back to the floor','Do not let hips fully touch before next rep'],
  ARRAY['Pushing through the lower back instead of glutes','Feet too far from body — shifts load to hamstrings','Not squeezing glutes at the top'],
  'The glute bridge is a beginner-friendly hip thrust. Master this before adding a barbell. Focus entirely on squeezing your glutes.',
  ARRAY['Hamstrings','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'cable-kickback', 'Cable Kickback', 'GLUTES', 'CABLE', '🏋️', false,
  ARRAY['Attach ankle strap to cable at low pulley','Face the machine holding for support','Kick working leg straight back squeezing the glute','Hold for 1 second at the top','Lower with control and repeat'],
  ARRAY['Swinging the leg with momentum','Kicking too high — becomes lower back','Not squeezing the glute at the top'],
  'The range of motion is small. Do not try to kick high. Focus on feeling the glute contract not on how far your leg goes back.',
  ARRAY['Hamstrings','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'sumo-deadlift', 'Sumo Deadlift', 'GLUTES', 'BARBELL', '🏋️', false,
  ARRAY['Stand with wide stance, toes pointed out 45 degrees','Grip bar inside your legs with overhand grip','Chest up, back flat, hips low to start','Drive through the floor and squeeze glutes at top','Lower bar with control hinging at hips'],
  ARRAY['Hips rising before the bar leaves the floor','Knees caving inward during the lift','Rounding the lower back'],
  'The sumo deadlift places more emphasis on glutes and inner thighs than conventional. Push your knees out in line with your toes throughout the entire lift.',
  ARRAY['Hamstrings','Adductors','Core','Traps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── CALVES ────────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'standing-calf-raises', 'Standing Calf Raises', 'CALVES', 'BODYWEIGHT', '🦵', true,
  ARRAY['Stand with balls of feet on edge of a step','Lower heels as far as possible for a deep stretch','Rise up on tiptoes as high as possible','Hold at the top for 1 second','Lower slowly over 3 seconds'],
  ARRAY['Not getting full range of motion either way','Bouncing at the bottom — loses the stretch benefit','Going too fast'],
  'Calves respond well to high reps and slow eccentrics. 3 seconds down every single rep. Full stretch at the bottom is non-negotiable.',
  ARRAY['Soleus','Achilles tendon'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'seated-calf-raises', 'Seated Calf Raises', 'CALVES', 'MACHINE', '🦵', false,
  ARRAY['Sit at machine with knees under the pad','Balls of feet on the platform, heels hanging','Lower heels for a full stretch','Rise up as high as possible','Squeeze and hold for 1 second at the top'],
  ARRAY['Not getting full range of motion','Too heavy weight — reduces range','Bouncing the weight'],
  'The seated position targets the soleus more than the gastrocnemius. Both exercises are needed for complete calf development.',
  ARRAY['Soleus'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'single-leg-calf-raise', 'Single Leg Calf Raise', 'CALVES', 'BODYWEIGHT', '🦵', true,
  ARRAY['Stand on one foot on edge of a step','Hold a wall or rail lightly for balance only','Lower heel as far as possible','Rise up as high as possible on one foot','Complete all reps on one side then switch'],
  ARRAY['Holding the wall too tightly — reduces the work','Not getting full range of motion','Going too fast'],
  'Single leg calf raises are significantly harder than two leg versions. If you can do 15 bodyweight you can add a dumbbell in the other hand.',
  ARRAY['Soleus','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── FULL BODY ─────────────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'kettlebell-swing', 'Kettlebell Swing', 'FULL_BODY', 'KETTLEBELL', '🏋️', false,
  ARRAY['Stand with feet shoulder width, kettlebell in front','Hinge at hips, swing kettlebell back between legs','Drive hips forward explosively to swing it to shoulder height','Let it swing back down and hinge to absorb','The power comes entirely from the hip drive not the arms'],
  ARRAY['Squatting instead of hinging','Using arms to lift — arms are just a link','Letting the bell pull you forward at the bottom'],
  'The kettlebell swing is a hip hinge not a squat. Think of snapping your hips forward like slamming a car door with your hips.',
  ARRAY['Glutes','Hamstrings','Core','Shoulders'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'burpees', 'Burpees', 'FULL_BODY', 'BODYWEIGHT', '🏋️', true,
  ARRAY['Stand with feet shoulder width','Drop hands to floor and jump feet back to plank','Perform a push up','Jump feet back to hands','Jump up with arms overhead'],
  ARRAY['Skipping the push up — reduces the upper body benefit','Landing with straight legs on the jump — knee stress','Going so fast that form breaks completely'],
  'Burpees are brutal but effective. Pace yourself — one good burpee beats ten sloppy ones. Land softly with knees slightly bent.',
  ARRAY['Chest','Shoulders','Core','Legs'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'box-jumps', 'Box Jumps', 'FULL_BODY', 'BODYWEIGHT', '🏋️', true,
  ARRAY['Stand in front of a box at knee to hip height','Bend knees and swing arms back','Explode up and forward landing on the box','Land softly with both feet, knees bent','Step down — do not jump down'],
  ARRAY['Box too high for your current level','Landing with straight legs','Jumping down instead of stepping — injury risk'],
  'Always step down from the box — never jump. The Achilles tendon is under massive stress on the landing. Start with a low box and build up.',
  ARRAY['Glutes','Hamstrings','Core','Calves'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'power-clean', 'Power Clean', 'FULL_BODY', 'BARBELL', '🏋️', false,
  ARRAY['Stand over bar, feet hip width, bar over mid-foot','Hinge down, grip just outside legs','First pull — lift bar to knee level keeping back flat','Second pull — explosive hip extension','Receive bar on shoulders in quarter squat position'],
  ARRAY['Pulling with the arms too early','Not getting full hip extension','Bar drifting away from the body'],
  'The power clean is a technical lift. Learn it with a broomstick first. The arms do not pull — they just guide. All power comes from the legs and hips.',
  ARRAY['Hamstrings','Glutes','Traps','Core'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

-- ── CORE (ADVANCED) ───────────────────────────────────────────────────

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'ab-wheel-rollout', 'Ab Wheel Rollout', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Kneel on floor holding ab wheel with both hands','Roll wheel forward slowly lowering body toward floor','Extend as far as you can while keeping back flat','Pull wheel back toward knees using core strength','Do not let hips sag at any point'],
  ARRAY['Hips sagging — lower back injury risk','Going too far too soon','Using hip flexors instead of abs to return'],
  'The ab wheel rollout is an advanced exercise. Start by rolling out just 30-40cm. Build range over weeks not days.',
  ARRAY['Shoulders','Lats','Triceps'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;

INSERT INTO exercises (id, name, muscle_group, equipment, icon, is_bodyweight, steps, common_mistakes, coach_tip, secondary_muscles, swap_alternatives, demo_video_url, muscle_diagram_url) VALUES (
  'dragon-flag', 'Dragon Flag', 'CORE', 'BODYWEIGHT', '🧘', true,
  ARRAY['Lie on bench, grip behind your head','Raise body to vertical keeping it completely straight','Lower body slowly keeping it rigid like a plank','Hover just above the bench at the bottom','Raise back up and repeat'],
  ARRAY['Bending at the hips — body must stay completely straight','Lowering too fast','Attempting before mastering basic core exercises'],
  'The dragon flag was popularised by Bruce Lee and is one of the hardest core exercises. Master planks, leg raises and ab wheel first.',
  ARRAY['Shoulders','Lats','Hip flexors'],
  '{}', NULL, NULL
) ON CONFLICT (id) DO NOTHING;
