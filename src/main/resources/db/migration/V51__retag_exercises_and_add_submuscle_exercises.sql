-- V51: Re-tag existing exercises to sub-muscle groups and add 10 new exercises.
--
-- Part 1: UPDATE muscle_group on existing rows so the QUADS, FRONT_DELTS,
--         SIDE_DELTS and REAR_DELTS sub-muscle tags are populated.
--         After this runs the LEGS tag will have 0 rows — intentional.
--
-- Part 2: INSERT 10 new exercises covering the sub-muscle groups created above.
--         ON CONFLICT (id) DO NOTHING makes the file safe to re-run.

-- ─────────────────────────────────────────────────────────────────────────────
-- PART 1 — RE-TAG EXISTING EXERCISES
-- ─────────────────────────────────────────────────────────────────────────────

-- Quad-dominant compound and isolation movements
UPDATE exercises
SET muscle_group = 'QUADS'
WHERE id IN (
    'squat',
    'leg-press',
    'goblet-squat',
    'lunges',
    'bulgarian-split-squat',
    'hack-squat',
    'step-ups',
    'leg-extension'
);

-- Front delt isolation
UPDATE exercises
SET muscle_group = 'FRONT_DELTS'
WHERE id = 'front-raises';

-- Side delt isolation
UPDATE exercises
SET muscle_group = 'SIDE_DELTS'
WHERE id IN (
    'lateral-raises',
    'cable-lateral-raise'
);

-- Rear delt / upper-back isolation
UPDATE exercises
SET muscle_group = 'REAR_DELTS'
WHERE id IN (
    'face-pulls',
    'reverse-flyes'
);

-- ─────────────────────────────────────────────────────────────────────────────
-- PART 2 — INSERT 10 NEW EXERCISES
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. DB Front Raise — FRONT_DELTS ─────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'db-front-raise',
    'DB Front Raise',
    'FRONT_DELTS',
    'DUMBBELL',
    '',
    '',
    '',
    false,
    ARRAY[
        'Stand with dumbbells resting at your thighs, palms facing your body',
        'Raise both arms forward to shoulder height with a slight elbow bend',
        'Do not rotate your palms or shrug your shoulders at the top',
        'Hold for 1 second at the top with dumbbells parallel to the floor',
        'Lower slowly over 3 seconds — the eccentric is where the work happens'
    ],
    ARRAY[
        'Swinging the torso to generate momentum — defeats the isolation',
        'Raising above shoulder height — puts the shoulder joint in a compromised position',
        'Using too much weight — front raises require strict form over load',
        'Shrugging the shoulders at the top — traps should stay down throughout'
    ],
    'Your front delts get significant work from every pressing movement you do. Treat front raises as a finishing exercise and keep the weight light enough to feel a burn at rep 12.',
    ARRAY['Upper chest', 'Serratus anterior'],
    ARRAY['front-raises', 'overhead-press', 'db-shoulder-press']
) ON CONFLICT (id) DO NOTHING;

-- 2. DB Lateral Raise (One-Arm) — SIDE_DELTS ──────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'db-lateral-raise',
    'DB Lateral Raise',
    'SIDE_DELTS',
    'DUMBBELL',
    '',
    '',
    '',
    false,
    ARRAY[
        'Stand holding one dumbbell at your side, other hand holding a fixed support',
        'Brace your core and keep your torso completely upright',
        'Raise the dumbbell out to the side until your arm is parallel to the floor',
        'Lead with your pinky finger slightly higher than your thumb at the top',
        'Lower slowly over 3 seconds — resist the weight on the way down'
    ],
    ARRAY[
        'Using momentum to swing the weight up — the movement must be controlled',
        'Raising the arm above shoulder height — strains the AC joint',
        'Bending the elbow too much — keep only a slight natural bend',
        'Shrugging the shoulder — upper traps take over from the side delt'
    ],
    'One-arm lateral raises let you stabilise with your free hand so you can focus entirely on the working delt. Lead with your pinky — that cue alone will double your side delt activation.',
    ARRAY['Upper traps', 'Supraspinatus'],
    ARRAY['lateral-raises', 'cable-lateral-raise', 'shoulder-press']
) ON CONFLICT (id) DO NOTHING;

-- 3. Plate Raise — SIDE_DELTS ─────────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'plate-raise',
    'Plate Raise',
    'SIDE_DELTS',
    'DUMBBELL',
    '',
    '',
    '',
    false,
    ARRAY[
        'Stand holding a weight plate with both hands at the 3 and 9 o''clock positions',
        'Keep arms nearly straight with just a slight elbow bend',
        'Raise the plate in front of you to shoulder height',
        'At shoulder height, tilt the plate slightly so the top edge tips away from you',
        'Lower slowly over 3 seconds back to the start position'
    ],
    ARRAY[
        'Using a plate that is too heavy — starts the rep with a jerk',
        'Raising far above shoulder height — rotator cuff stress',
        'Not tilting the plate at the top — that rotation increases side delt engagement',
        'Rushing the lowering phase — the slow eccentric is half the stimulus'
    ],
    'The plate keeps both hands in a fixed position which reduces cheating and keeps constant tension on the delts. Use 5kg or 10kg — this is not a strength exercise.',
    ARRAY['Front delts', 'Upper traps'],
    ARRAY['lateral-raises', 'db-lateral-raise', 'front-raises']
) ON CONFLICT (id) DO NOTHING;

-- 4. DB Reverse Flye (Seated) — REAR_DELTS ────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'db-reverse-flye',
    'DB Reverse Flye',
    'REAR_DELTS',
    'DUMBBELL',
    '',
    '',
    '',
    false,
    ARRAY[
        'Sit at the end of a bench and hinge forward until your chest rests on your thighs',
        'Hold dumbbells hanging straight down, palms facing each other',
        'Raise both arms out to the sides keeping a slight bend in the elbows',
        'Squeeze your rear delts and rhomboids hard at the top',
        'Lower slowly back to the start — do not let the weights swing'
    ],
    ARRAY[
        'Shrugging the shoulders — traps take over from the rear delts',
        'Using momentum by bouncing the torso — the torso must stay still',
        'Going too heavy — rear delts are small and respond best to light strict work',
        'Not hinging far enough forward — reduces rear delt isolation'
    ],
    'Most people have underdeveloped rear delts because they never train them directly. Keep the weight embarrassingly light and focus on squeezing your shoulder blades together at the top.',
    ARRAY['Rhomboids', 'Lower traps', 'Infraspinatus'],
    ARRAY['face-pulls', 'reverse-flyes', 'band-pull-apart']
) ON CONFLICT (id) DO NOTHING;

-- 5. Band Pull-Apart — REAR_DELTS ──────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'band-pull-apart',
    'Band Pull-Apart',
    'REAR_DELTS',
    'BAND',
    '',
    '',
    '',
    false,
    ARRAY[
        'Hold a resistance band at shoulder height with hands wider than shoulder width',
        'Keep arms nearly straight with just a slight elbow bend',
        'Pull both ends of the band apart by driving elbows back and out',
        'Squeeze rear delts and shoulder blades together when arms are fully apart',
        'Return slowly — maintain tension in the band at all times'
    ],
    ARRAY[
        'Pulling with bent elbows — this becomes a row not a pull-apart',
        'Letting the band snap back — the slow return is half the work',
        'Using a band so light there is no real resistance',
        'Dropping the elbows during the pull — keep everything at shoulder height'
    ],
    'Band pull-aparts are one of the best warm-up exercises for shoulder health. Do 20 reps before any pressing session and your shoulder joint will thank you.',
    ARRAY['Rhomboids', 'External rotators', 'Middle traps'],
    ARRAY['face-pulls', 'reverse-flyes', 'db-reverse-flye']
) ON CONFLICT (id) DO NOTHING;

-- 6. Single-Leg Romanian Deadlift — HAMSTRINGS ────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'single-leg-rdl',
    'Single-Leg Romanian Deadlift',
    'HAMSTRINGS',
    'DUMBBELL',
    '',
    '',
    '',
    false,
    ARRAY[
        'Stand holding a dumbbell in one hand, soft bend in the standing knee',
        'Hinge at the hip and lower the dumbbell toward the floor',
        'The opposite leg lifts behind you as a counterbalance — keep it in line with your spine',
        'Lower until you feel a strong stretch in the standing hamstring',
        'Drive through the standing heel to return to upright — squeeze the glute at the top'
    ],
    ARRAY[
        'Rotating the hips open — the rear leg must stay parallel to the floor',
        'Rounding the lower back — keep the spine neutral throughout',
        'Bending the standing knee excessively — this becomes a single-leg squat',
        'Not getting a full hamstring stretch — go until your back leg is parallel to the floor'
    ],
    'The single-leg RDL builds hip stability and hamstring strength simultaneously. Start with a light dumbbell held in the opposite hand to the working leg — this creates a cross-body stability challenge.',
    ARRAY['Glutes', 'Core', 'Calves', 'Adductors'],
    ARRAY['romanian-deadlift', 'lying-leg-curl', 'leg-curl']
) ON CONFLICT (id) DO NOTHING;

-- 7. Glute-Ham Raise — HAMSTRINGS ─────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'glute-ham-raise',
    'Glute-Ham Raise',
    'HAMSTRINGS',
    'BODYWEIGHT',
    '',
    '',
    '',
    true,
    ARRAY[
        'Lock your feet under a fixed pad on a GHR machine or have a partner hold your ankles',
        'Start with your body upright and lower your torso forward slowly by extending at the knee',
        'Keep your hips extended and body straight from knee to shoulder at all times',
        'At the bottom use your hamstrings to curl your body back up to vertical',
        'Beginners: lower down slowly and use hands to push back up until strong enough to pull unaided'
    ],
    ARRAY[
        'Hinging at the hips — the movement must come entirely from the knee joint',
        'Dropping too fast at the bottom — the slow eccentric is the most valuable part',
        'Not locking the hips in extension — a bent hip position removes the hamstring stretch',
        'Attempting before you can do 10 clean nordic curls'
    ],
    'The glute-ham raise is one of the most demanding hamstring exercises in existence. Master the nordic curl first. If you cannot complete the concentric pull, use your hands to push back up and focus solely on the slow lowering phase.',
    ARRAY['Glutes', 'Calves', 'Erector spinae'],
    ARRAY['nordic-curl', 'lying-leg-curl', 'romanian-deadlift']
) ON CONFLICT (id) DO NOTHING;

-- 8. Cable Pull-Through — GLUTES ──────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'cable-pull-through',
    'Cable Pull-Through',
    'GLUTES',
    'CABLE',
    '',
    '',
    '',
    false,
    ARRAY[
        'Set the cable to the lowest pulley and attach a rope handle',
        'Face away from the machine and straddle the cable, holding the rope between your legs',
        'Hinge at the hips and let the cable pull your hands back through your legs',
        'Drive your hips forward explosively to stand up tall, squeezing your glutes hard at lockout',
        'Hold the top position for 1 second before hinging back into the next rep'
    ],
    ARRAY[
        'Squatting instead of hinging — all movement must come from the hips not the knees',
        'Not reaching full hip extension at the top — partial reps reduce glute activation',
        'Pulling with the arms — the arms are just a link, all power is from the hip drive',
        'Standing too far from the machine — reduces the effective resistance angle'
    ],
    'The cable pull-through teaches the hip hinge pattern with constant cable tension throughout the movement. Think of it as a deadlift drill — drive your hips forward and squeeze your glutes at the top.',
    ARRAY['Hamstrings', 'Erector spinae', 'Core'],
    ARRAY['hip-thrust', 'glute-bridge', 'cable-kickback']
) ON CONFLICT (id) DO NOTHING;

-- 9. Incline DB Curl — BICEPS ─────────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'incline-db-curl',
    'Incline DB Curl',
    'BICEPS',
    'DUMBBELL',
    '',
    '',
    '',
    false,
    ARRAY[
        'Set an incline bench to 45-60 degrees and sit back with a dumbbell in each hand',
        'Let your arms hang fully extended behind the line of your torso — this is the stretched position',
        'Curl both dumbbells up toward your shoulders keeping your upper arms still',
        'Supinate your wrists at the top — turn palms fully toward the ceiling',
        'Lower slowly back to full extension — do not let arms swing forward'
    ],
    ARRAY[
        'Letting the upper arms drift forward — this removes the stretched position advantage',
        'Not lowering to full arm extension — the stretched position is the entire point of this exercise',
        'Using too much weight — you will always be weaker here than on a standing curl',
        'Rushing the lowering phase — a 3-second eccentric on every rep doubles the stimulus'
    ],
    'The incline position puts the bicep in a fully lengthened position at the bottom of every rep. This is one of the most effective positions for bicep growth. You will need to drop the weight significantly from your standing curl — that is completely normal.',
    ARRAY['Brachialis', 'Brachioradialis', 'Forearms'],
    ARRAY['bicep-curl', 'hammer-curl', 'concentration-curl']
) ON CONFLICT (id) DO NOTHING;

-- 10. Diamond Push-Ups — TRICEPS ──────────────────────────────────────────────
INSERT INTO exercises (
    id, name, muscle_group, equipment, icon,
    demo_video_url, muscle_diagram_url,
    is_bodyweight, steps, common_mistakes,
    coach_tip, secondary_muscles, swap_alternatives
) VALUES (
    'diamond-push-ups',
    'Diamond Push-Ups',
    'TRICEPS',
    'BODYWEIGHT',
    '',
    '',
    '',
    true,
    ARRAY[
        'Start in a high plank position with hands close together under your chest',
        'Touch index fingers and thumbs together to form a diamond shape',
        'Lower your chest toward your hands keeping elbows tucked close to your body',
        'Chest should touch or nearly touch the back of your hands at the bottom',
        'Press back up until arms are fully extended and repeat'
    ],
    ARRAY[
        'Flaring elbows out wide — this becomes a chest exercise not a tricep exercise',
        'Placing hands too far forward — puts excessive stress on the wrists',
        'Partial reps at the top — full lockout is essential to maximise tricep activation',
        'Hips sagging or rising — maintain a rigid plank position throughout'
    ],
    'Diamond push-ups are the most effective bodyweight tricep exercise. Keep your elbows pinned to your ribs and think about driving the floor away with your triceps at the top of each rep.',
    ARRAY['Chest', 'Front delts', 'Core'],
    ARRAY['close-grip-bench', 'tricep-pushdowns', 'dips']
) ON CONFLICT (id) DO NOTHING;
