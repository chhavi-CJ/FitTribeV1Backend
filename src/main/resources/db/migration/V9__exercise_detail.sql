-- V9: Seed exercise steps, common_mistakes, coach_tip, secondary_muscles

UPDATE exercises SET
  steps           = ARRAY['Set up with feet flat, back slightly arched','Grip bar just wider than shoulder width','Lower bar to lower chest in 2-3 seconds','Touch lightly — do not bounce','Press up and slightly back toward rack'],
  common_mistakes = ARRAY['Bouncing bar off chest — loses time under tension','Flaring elbows 90° out — rotator cuff risk','Lifting hips off bench','Partial reps — go full range'],
  coach_tip       = 'Drive your feet into the floor throughout the press. Squeeze the bar as if trying to bend it inward.',
  secondary_muscles = ARRAY['Front delts','Triceps','Serratus anterior']
WHERE id = 'bench-press';

UPDATE exercises SET
  steps           = ARRAY['Sit with back supported, bar at collarbone','Grip bar at shoulder width, elbows at 90°','Press directly overhead until arms are straight','Lower to chin level — do not go lower','Re-rack safely after final rep'],
  common_mistakes = ARRAY['Pressing in front of face instead of directly overhead','Arching lower back excessively','Using momentum — strict form builds more strength'],
  coach_tip       = 'Keep your core braced throughout. If your lower back arches noticeably, reduce the weight.',
  secondary_muscles = ARRAY['Triceps','Upper traps','Serratus anterior']
WHERE id = 'shoulder-press';

UPDATE exercises SET
  steps           = ARRAY['Set bench to 30-45 degrees','Hold dumbbells at shoulder level, palms forward','Press up and slightly inward in a slight arc','Lower slowly until a stretch is felt in the upper chest','Keep elbows at 70-75 degrees throughout'],
  common_mistakes = ARRAY['Bench angle above 45° — becomes a shoulder press','Not controlling the descent','Touching dumbbells at the top — reduces tension'],
  coach_tip       = 'The 30° incline targets upper chest optimally. Higher angles shift load to shoulders.',
  secondary_muscles = ARRAY['Front delts','Triceps']
WHERE id = 'incline-db-press';

UPDATE exercises SET
  steps           = ARRAY['Stand with dumbbells at sides','Raise arms to shoulder level with a slight elbow bend','Lead with your elbows, not your wrists','Lower slowly in 2-3 seconds','Do not swing or shrug at any point'],
  common_mistakes = ARRAY['Using momentum to swing the weight up','Going above shoulder height','Shrugging at the top','Too heavy — defeats the isolation purpose'],
  coach_tip       = 'If you need to lean or swing, the weight is too heavy. Lighter and stricter always wins on lateral raises.',
  secondary_muscles = ARRAY['Upper traps']
WHERE id = 'lateral-raises';

UPDATE exercises SET
  steps           = ARRAY['Set cable at head height with straight bar','Grip with overhand grip, elbows at sides','Keep elbows pinned to your sides throughout','Extend fully — lock out at the bottom','Return slowly without letting elbows drift'],
  common_mistakes = ARRAY['Letting elbows drift forward during the movement','Not reaching full extension at the bottom','Using shoulder muscles to initiate the pull'],
  coach_tip       = 'Elbows should not move at all. If they drift, reduce the weight and focus on the lockout.',
  secondary_muscles = ARRAY['Anconeus','Forearm extensors']
WHERE id = 'tricep-pushdowns';

UPDATE exercises SET
  steps           = ARRAY['Set bar on upper traps below your neck','Feet shoulder width, toes slightly outward','Brace core hard and sit back into the squat','Lower until thighs are parallel to the floor','Drive through heels and stand explosively'],
  common_mistakes = ARRAY['Knees caving inward — very common in beginners','Heels rising — indicates poor ankle mobility','Excessive forward lean — focus on keeping chest up'],
  coach_tip       = 'Think about sitting back and down, not just down. Keep your chest proud and proud throughout the movement.',
  secondary_muscles = ARRAY['Glutes','Hamstrings','Core','Calves','Adductors']
WHERE id = 'squat';

UPDATE exercises SET
  steps           = ARRAY['Set feet hip width on the platform','Lower the platform until knees reach 90°','Do not lock out knees at the top of the movement','Control the descent for 2 seconds','Push through your full foot, not just the toes'],
  common_mistakes = ARRAY['Feet too low — puts excessive stress on knees','Locking out knees at the top — joint impact','Bouncing off the bottom with heavy weight'],
  coach_tip       = 'Foot position changes emphasis. Higher feet targets glutes and hamstrings. Lower hits quads harder.',
  secondary_muscles = ARRAY['Glutes','Hamstrings','Calves']
WHERE id = 'leg-press';

UPDATE exercises SET
  steps           = ARRAY['Stand with bar at hip level, slight knee bend','Hinge at the hips by pushing them backward','Lower bar along your shins until hamstring stretch','Keep back perfectly neutral throughout','Drive hips forward to return to standing'],
  common_mistakes = ARRAY['Rounding the lower back — serious injury risk','Bending knees too much — becomes a conventional deadlift','Not feeling the hamstring stretch — reduce weight'],
  coach_tip       = 'Feel the stretch in your hamstrings before you reverse. If you feel it in your back, the weight is too heavy.',
  secondary_muscles = ARRAY['Glutes','Erector spinae','Adductors']
WHERE id = 'romanian-deadlift';

UPDATE exercises SET
  steps           = ARRAY['Lie face down on machine, pad just above ankles','Adjust pad position so it sits comfortably','Curl heels toward glutes in a controlled motion','Hold for 1 second at the top contraction','Lower slowly over 3 seconds — do not drop'],
  common_mistakes = ARRAY['Hips rising off the pad during the movement','Not reaching a full range of motion','Using momentum to curl the weight up'],
  coach_tip       = 'Think about squeezing your hamstring at the top. The slow eccentric (lowering) is where the growth happens.',
  secondary_muscles = ARRAY['Calves','Glutes']
WHERE id = 'leg-curl';

UPDATE exercises SET
  steps           = ARRAY['Hang with arms fully extended, shoulder width grip','Pull shoulders down and back first (depress scapulae)','Pull elbows down toward your ribcage','Chin clears the bar at the top','Lower fully — do not skip the dead hang at the bottom'],
  common_mistakes = ARRAY['Kipping or swinging to get up','Partial reps that avoid the bottom dead hang','Not engaging scapulae before pulling'],
  coach_tip       = 'Begin each rep by depressing your shoulder blades. This protects your shoulders and activates far more lat.',
  secondary_muscles = ARRAY['Biceps','Rear delts','Teres major','Core']
WHERE id = 'pull-ups';

UPDATE exercises SET
  steps           = ARRAY['Stand with feet hip width, bar over mid-foot','Hinge to 45 degrees, bar hanging at arm length','Pull bar to your lower chest or belly','Squeeze shoulder blades together at the top','Lower with full control — do not drop'],
  common_mistakes = ARRAY['Too upright a torso — becomes more of a shrug','Jerking the weight up with momentum','Pulling to the stomach — changes the back angle engagement'],
  coach_tip       = 'Aim for your lower chest with the bar. Drive your elbows back and squeeze your upper back at the top.',
  secondary_muscles = ARRAY['Biceps','Rear delts','Erector spinae']
WHERE id = 'barbell-row';

UPDATE exercises SET
  steps           = ARRAY['Grip bar 1.5x shoulder width with overhand grip','Sit with knees under the pad, lean back slightly','Pull bar to your upper chest','Squeeze lats hard at the bottom','Control the bar back up slowly — do not let it fly'],
  common_mistakes = ARRAY['Pulling the bar behind the neck — neck injury risk','Leaning back too far — becomes a row instead','Not completing the full range of motion'],
  coach_tip       = 'Imagine pulling your elbows down to your back pockets. This single cue transforms lat activation.',
  secondary_muscles = ARRAY['Biceps','Rear delts','Rhomboids','Teres major']
WHERE id = 'lat-pulldown';

UPDATE exercises SET
  steps           = ARRAY['Stand with dumbbells at sides, palms facing forward','Curl up keeping elbows completely fixed at sides','Supinate your wrists at the top for peak contraction','Hold 1 second at the peak','Lower fully in 2-3 seconds — do not drop'],
  common_mistakes = ARRAY['Swinging elbows forward to get more range','Partial reps that avoid the full stretch at bottom','Using lower back momentum on heavier sets'],
  coach_tip       = 'Lower all the way for the full stretch. The eccentric stretch is as important as the peak contraction.',
  secondary_muscles = ARRAY['Brachialis','Brachioradialis','Forearm flexors']
WHERE id = 'bicep-curl';

UPDATE exercises SET
  steps           = ARRAY['Set cables at shoulder height on both sides','Step forward into a split stance for stability','Bring handles together in an arc motion with slight elbow bend','Slight bend in elbows maintained throughout','Squeeze chest hard at the centre, hold 1 second'],
  common_mistakes = ARRAY['Straightening elbows — turns it into a tricep movement','Going too heavy — loses the arc motion entirely','Not squeezing at the peak contraction'],
  coach_tip       = 'Think about trying to hug a large tree. The arc motion is what makes this an effective isolation exercise.',
  secondary_muscles = ARRAY['Front delts','Biceps short head']
WHERE id = 'cable-flyes';

UPDATE exercises SET
  steps           = ARRAY['Hands slightly wider than shoulder width on the floor','Body in a rigid plank position from head to heels','Lower until chest almost touches the floor','Press back up to full arm extension','Do not let hips sag or pike up'],
  common_mistakes = ARRAY['Hips sagging down — lower back strain','Head looking up instead of neutral','Partial range of motion — not going low enough'],
  coach_tip       = 'Squeeze your glutes and abs throughout. A push-up is a moving plank — treat it that way.',
  secondary_muscles = ARRAY['Front delts','Triceps','Core','Serratus anterior']
WHERE id = 'push-ups';

UPDATE exercises SET
  steps           = ARRAY['Grip parallel bars with straight arms, body vertical','Lean slightly forward for more chest involvement','Lower until elbows reach 90°','Press back up without fully locking out','Control the descent — 2-3 seconds down'],
  common_mistakes = ARRAY['Going too deep below 90° — shoulder impingement risk','Flaring elbows wide — shoulder joint stress','Rushing the movement with partial reps'],
  coach_tip       = 'The more you lean forward, the more chest involvement. Staying upright isolates triceps more directly.',
  secondary_muscles = ARRAY['Chest','Front delts','Core']
WHERE id = 'dips';

UPDATE exercises SET
  steps           = ARRAY['Stand with feet shoulder width, bar at collarbone','Brace core hard before you press','Press directly overhead until arms are completely straight','Finish with arms vertical, biceps near ears','Lower under control to collarbone — do not drop'],
  common_mistakes = ARRAY['Pressing in an arc instead of straight up','Lower back overextension — core not braced','Bar drifting forward of the body on the way up'],
  coach_tip       = 'Push your head through the window as the bar passes your face. This keeps the bar path perfectly vertical.',
  secondary_muscles = ARRAY['Triceps','Upper traps','Core','Serratus anterior']
WHERE id = 'overhead-press';

UPDATE exercises SET
  steps           = ARRAY['Start on forearms and toes in a straight line','Body straight from head to heels — no piking','Brace abs hard and squeeze glutes','Hold position breathing normally','Do not hold your breath — it reduces stability'],
  common_mistakes = ARRAY['Hips too high or too low during the hold','Holding breath instead of breathing normally','Head hanging down between the shoulders'],
  coach_tip       = 'Focus on pulling your belly button toward your spine. A 30-second perfect plank beats a 2-minute saggy one.',
  secondary_muscles = ARRAY['Glutes','Shoulders','Erector spinae','Hip flexors']
WHERE id = 'plank';

UPDATE exercises SET
  steps           = ARRAY['Lie on back with knees bent at 90°','Hands at temples — not clasped behind the neck','Curl shoulders off the floor toward your knees','Hold 1 second at the top of the crunch','Lower with full control — do not drop back down'],
  common_mistakes = ARRAY['Pulling on the neck with hands — cervical strain','Using momentum to bounce through reps','Not fully lowering — skipping the stretch at bottom'],
  coach_tip       = 'You only need to raise your shoulders 20-30cm. Going further brings hip flexors in and reduces ab work.',
  secondary_muscles = ARRAY['Hip flexors','Obliques']
WHERE id = 'crunches';
