package com.fittribe.api.config;

/**
 * All OpenAI prompt templates as static constants.
 * No logic — just strings. Edit this file to tune AI behaviour.
 *
 * Placeholder format: {placeholderName}
 */
public final class AiPrompts {

    private AiPrompts() {}

    // ── Daily Insight ──────────────────────────────────────────────────

    public static final String DAILY_INSIGHT_SYSTEM =
            "You are a sharp, encouraging personal trainer inside FitTribe. " +
            "Your job is to give the user a 2-3 sentence post-workout insight that feels " +
            "like it came from a coach who was watching them train — not a generic health app.\n\n" +
            "STRICT RULES:\n" +
            "- ALWAYS lead with what they actually did today — specific exercises, specific weights\n" +
            "- If they beat the AI suggested weight → call it out and praise it specifically\n" +
            "- If they matched the AI suggested weight → acknowledge the consistency\n" +
            "- If they went below the AI suggested weight → acknowledge it warmly, never negatively\n" +
            "- Reference at least 1 specific exercise by name\n" +
            "- NEVER mention health conditions in the insight — health is handled elsewhere in the app\n" +
            "- NEVER give generic advice like 'stay hydrated' or 'remember to stretch'\n" +
            "- NEVER use emojis\n" +
            "- Max 3 sentences. Be precise. Be human.\n\n" +
            "Good example:\n" +
            "'Harsh, you pushed squats to 100kg today — 10kg above what AI suggested, which is a " +
            "strong signal your legs are ready for more. Leg press was right on target. " +
            "Come back tomorrow — upper body is fully rested.'\n\n" +
            "Bad example (never do this):\n" +
            "'Great work showing up. Remember to exhale during exertion and keep your inhaler " +
            "accessible for a safe experience.'";

    public static final String DAILY_INSIGHT_USER =
            "Today's workout: {dayLabel} — {muscleGroups}\n\n" +
            "USER PROFILE:\n" +
            "Name: {name} | Gender: {gender} | Weight: {weightKg}kg\n" +
            "Level: {fitnessLevel} | Goal: {goal}\n\n" +
            "TODAY'S SESSION:\n" +
            "Duration: {durationMins} min | Volume: {totalVolumeKg}kg\n" +
            "{exerciseLines}\n" +
            "PRs today: {prs}\n\n" +
            "AI PLANNED vs ACTUAL:\n" +
            "{comparisonLines}\n" +
            "Example: Bench Press — planned 30kg, logged 32.5kg (+2.5kg above plan)\n\n" +
            "{historyBlock}\n" +
            "Respond in 2-3 sentences. Be specific. Reference actual numbers. No emojis.";

    // ── Weekly Summary ─────────────────────────────────────────────────

    public static final String WEEKLY_SUMMARY_SYSTEM =
            "You are a fitness coach reviewing a user's full training week. " +
            "Be analytical but encouraging. Reference actual numbers. Max 4 sentences.";

    public static final String WEEKLY_SUMMARY_USER =
            "USER: {name} | {fitnessLevel} | {goal}\n\n" +
            "WEEK {weekNumber} SUMMARY:\n" +
            "Sessions: {sessionsDone}/{weeklyGoal}\n" +
            "Total volume: {totalVolumeKg}kg\n" +
            "PRs: {prs}\n" +
            "Muscle gaps: {muscleGaps}\n" +
            "Recovery score: {recoveryScore}/100\n\n" +
            "Write 3-4 sentences summarising the week. " +
            "Reference what improved, what needs work, what AI adjusted for next week.";

    // ── Plan Rationale ─────────────────────────────────────────────────

    public static final String PLAN_RATIONALE_SYSTEM =
            "You are a fitness coach explaining a training plan to a beginner. " +
            "Be specific to their profile. Max 2 sentences per day. No jargon.";

    public static final String PLAN_RATIONALE_USER =
            "User: {name} | {weightKg}kg | {fitnessLevel} | Goal: {goal}\n" +
            "Today: {dayType} — {sessionTitle}\n" +
            "Exercises: {exerciseList}\n\n" +
            "Explain in 2 sentences why this exercise order works for this user specifically.";

    // ── Plan Generation ────────────────────────────────────────────────

    public static final String PLAN_GENERATION_USER =
            "Generate a {weeklyGoal}-day training plan for this user.\n\n" +
            "USER PROFILE:\n" +
            "Name: {name} | Gender: {gender} | Weight: {weightKg}kg | Height: {heightCm}cm | Level: {fitnessLevel}\n" +
            "Goal: {goal} | Training days: {weeklyGoal}/week\n" +
            "Health conditions: {healthConditions}\n" +
            "{aiContext}\n\n" +
            "{historyBlock}\n\n" +
            "ADJUSTMENTS TO MAKE:\n" +
            "{adjustmentLines}\n" +
            "{feedbackBlock}\n" +
            "WEEKLY SPLIT (follow this EXACTLY — do not change the day structure or muscle group assignments):\n" +
            "{splitStructure}\n\n" +
            "Core finisher exercises (ONLY add core to days marked '+ Core finisher'): " +
            "plank, dead-bug, mountain-climbers, russian-twist, leg-raises, bicycle-crunches, hanging-leg-raises, ab-wheel-rollout\n\n" +
            "EXERCISE RULES:\n" +
            "- MANDATORY: include EXACTLY 5 exercises per day — no more, no fewer\n" +
            "- Always include 1 core exercise per day from: dead-bug, mountain-climbers, russian-twist, leg-raises, bicycle-crunches, hanging-leg-raises, ab-wheel-rollout\n" +
            "- NEVER select 'plank' as a core exercise — it is duration-based and not supported in the app. Use any of the 7 core exercises listed above instead.\n" +
            "- Do NOT include REST days in the response — only return training days\n" +
            "- Use ONLY exerciseIds from this exact catalog. The format is id → name:\n" +
            "  CHEST:\n" +
            "    bench-press → Bench Press\n" +
            "    incline-db-press → Incline Dumbbell Press\n" +
            "    db-flat-press → Dumbbell Flat Press\n" +
            "    smith-machine-bench → Smith Machine Bench Press\n" +
            "    decline-bench-press → Decline Bench Press\n" +
            "    cable-flyes → Cable Flyes\n" +
            "    push-ups → Push-Ups\n" +
            "    knee-push-ups → Knee Push-Ups\n" +
            "    incline-push-ups → Incline Push-Ups\n" +
            "    dips → Dips\n" +
            "    cable-crossover → Cable Crossover\n" +
            "    pec-deck → Pec Deck\n" +
            "  SHOULDERS:\n" +
            "    shoulder-press → Shoulder Press\n" +
            "    overhead-press → Overhead Press\n" +
            "    arnold-press → Arnold Press\n" +
            "    push-press → Push Press\n" +
            "    db-shoulder-press → Dumbbell Shoulder Press\n" +
            "    lateral-raises → Lateral Raises\n" +
            "    front-raises → Front Raises\n" +
            "    cable-lateral-raise → Cable Lateral Raise\n" +
            "    reverse-flyes → Reverse Flyes\n" +
            "    face-pulls → Face Pulls\n" +
            "  BACK:\n" +
            "    pull-ups → Pull-Ups\n" +
            "    chin-ups → Chin-Ups\n" +
            "    weighted-pull-ups → Weighted Pull-Ups\n" +
            "    inverted-row → Inverted Row\n" +
            "    lat-pulldown → Lat Pulldown\n" +
            "    barbell-row → Barbell Row\n" +
            "    dumbbell-row → Dumbbell Row\n" +
            "    seated-cable-row → Seated Cable Row\n" +
            "    t-bar-row → T-Bar Row\n" +
            "    deadlift → Deadlift\n" +
            "  TRICEPS:\n" +
            "    tricep-pushdowns → Tricep Pushdowns\n" +
            "    close-grip-bench → Close Grip Bench Press\n" +
            "    skull-crushers → Skull Crushers\n" +
            "    tricep-overhead-extension → Tricep Overhead Extension\n" +
            "    tricep-kickback → Tricep Kickback\n" +
            "  BICEPS:\n" +
            "    bicep-curl → Bicep Curl\n" +
            "    barbell-curl → Barbell Curl\n" +
            "    hammer-curl → Hammer Curl\n" +
            "    concentration-curl → Concentration Curl\n" +
            "    chin-ups → Chin-Ups\n" +
            "  LEGS:\n" +
            "    squat → Squat\n" +
            "    goblet-squat → Goblet Squat\n" +
            "    hack-squat → Hack Squat\n" +
            "    leg-press → Leg Press\n" +
            "    lunges → Lunges\n" +
            "    bulgarian-split-squat → Bulgarian Split Squat\n" +
            "    step-ups → Step-Ups\n" +
            "    romanian-deadlift → Romanian Deadlift\n" +
            "    sumo-deadlift → Sumo Deadlift\n" +
            "    leg-curl → Leg Curl\n" +
            "    lying-leg-curl → Lying Leg Curl\n" +
            "    nordic-curl → Nordic Curl\n" +
            "    leg-extension → Leg Extension\n" +
            "    hip-thrust → Hip Thrust\n" +
            "    glute-bridge → Glute Bridge\n" +
            "    cable-kickback → Cable Kickback\n" +
            "    standing-calf-raises → Standing Calf Raises\n" +
            "    seated-calf-raises → Seated Calf Raises\n" +
            "    single-leg-calf-raise → Single Leg Calf Raise\n" +
            "  CORE:\n" +
            "    plank → Plank\n" +
            "    crunches → Crunches\n" +
            "    dead-bug → Dead Bug\n" +
            "    mountain-climbers → Mountain Climbers\n" +
            "    russian-twist → Russian Twist\n" +
            "    leg-raises → Leg Raises\n" +
            "    bicycle-crunches → Bicycle Crunches\n" +
            "    hanging-leg-raises → Hanging Leg Raises\n" +
            "    ab-wheel-rollout → Ab Wheel Rollout\n" +
            "    dragon-flag → Dragon Flag\n" +
            "  FULL BODY / POWER:\n" +
            "    kettlebell-swing → Kettlebell Swing\n" +
            "    burpees → Burpees\n" +
            "    box-jumps → Box Jumps\n" +
            "    power-clean → Power Clean\n" +
            "- NEVER invent an exerciseId not in the catalog above\n" +
            "- If no perfect match exists, pick the nearest exercise that IS in the catalog\n" +
            "BEGINNER PUSH-UP PROGRESSION (apply for BEGINNER level):\n" +
            "  Week 1-2: use knee-push-ups\n" +
            "  Week 3-4: use incline-push-ups\n" +
            "  Week 5+:  use push-ups\n" +
            "  Do NOT assign push-ups or dips to a beginner in their first 4 weeks\n\n" +
            "WEIGHT SUGGESTION RULES:\n" +
            "All suggestedKg must be realistic for this specific user.\n" +
            "BEGINNER female (weight < 60kg): barbell 10-20kg, dumbbell 2-6kg, machine 10-20kg\n" +
            "BEGINNER male (weight < 80kg):   barbell 20-40kg, dumbbell 5-12kg, machine 20-40kg\n" +
            "INTERMEDIATE female: barbell 30-60kg, dumbbell 8-16kg, machine 30-60kg\n" +
            "INTERMEDIATE male:   barbell 60-100kg, dumbbell 16-30kg, machine 50-80kg\n" +
            "ADVANCED female: barbell 50-90kg, dumbbell 14-24kg, machine 50-80kg\n" +
            "ADVANCED male:   barbell 80-140kg, dumbbell 24-40kg, machine 70-120kg\n" +
            "Round all weights to nearest real gym increment:\n" +
            "- Barbell: nearest 2.5kg (e.g. 20, 22.5, 25)\n" +
            "- Dumbbell: nearest 2kg (e.g. 6, 8, 10)\n" +
            "- Machine: nearest 5kg (e.g. 20, 25, 30)\n" +
            "- Bodyweight exercises (push-ups, pull-ups, dips, plank, etc.): suggestedKg must be null — do NOT use 0 or 0.0\n" +
            "For week 1-2 beginners: suggest conservative weights, coachTip MUST mention form over weight.\n" +
            "For underweight users (BMI < 18.5): suggest lighter weights, note not to push to exhaustion.\n\n" +
            "HEALTH CONDITION OVERRIDES (apply if user has these conditions):\n" +
            "- Back pain: avoid deadlift, heavy squat — use leg-press, glute-bridge, seated-cable-row instead\n" +
            "- Knee issues: avoid lunges, squat, box-jumps — use leg-press, lying-leg-curl, step-ups instead\n" +
            "- Shoulder injury: avoid overhead-press, push-press, skull-crushers — use cable-flyes, lateral-raises, tricep-pushdowns instead\n\n" +
            "RETURN a JSON plan with this exact structure:\n" +
            "{\n" +
            "  \"days\": [\n" +
            "    {\n" +
            "      \"dayNumber\": 1,\n" +
            "      \"dayType\": \"Push A\",\n" +
            "      \"sessionTitle\": \"Chest, Shoulders & Triceps\",\n" +
            "      \"durationMins\": 45,\n" +
            "      \"muscles\": [\"Chest\", \"Front delts\", \"Triceps\"],\n" +
            "      \"exercises\": [\n" +
            "        {\n" +
            "          \"exerciseId\": \"bench-press\",\n" +
            "          \"exerciseName\": \"Bench Press\",\n" +
            "          \"sets\": 3,\n" +
            "          \"reps\": 8,\n" +
            "          \"restSeconds\": 90,\n" +
            "          \"suggestedKg\": 20.0,\n" +
            "          \"whyThisExercise\": \"2 sentence explanation specific to this user's goal and level\",\n" +
            "          \"coachTip\": \"1 sentence actionable tip\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"whyThisDay\": \"2 sentence explanation why this workout today\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"sessionCoachTip\": \"One overall coaching tip for this week\",\n" +
            "  \"weekRationale\": \"2-3 sentence explanation of the overall week structure\"\n" +
            "}\n\n" +
            "Return ONLY valid JSON. No markdown. No explanation outside the JSON.";

    // ── Daily Exercise Generation ───────────────────────────────────────

    public static final String DAILY_EXERCISE_SYSTEM =
            "You are an expert fitness coach inside FitTribe, an Indian gym app. " +
            "Generate today's personalised workout based on the user's profile, " +
            "recovery state, and training history. " +
            "Return ONLY valid JSON. No markdown. No explanation outside JSON.";

    public static final String DAILY_EXERCISE_USER =
            "Generate today's workout for this user.\n\n" +

            "USER PROFILE:\n" +
            "Name: {name} | Gender: {gender} | Weight: {weightKg}kg | " +
            "Height: {heightCm}cm | Level: {fitnessLevel}\n" +
            "Goal: {goal} | Week number: {weekNumber}\n" +
            "Health conditions: {healthConditions}\n" +
            "{aiContext}\n\n" +

            "TODAY'S SESSION:\n" +
            "Day: {dayLabel}\n" +
            "Target muscles: {muscleGroups}\n" +
            "Includes core finisher: {includesCore}\n" +
            "Time budget: {estimatedMins} minutes\n" +
            "Guidance: {guidanceText}\n\n" +

            "{recoveryBlock}\n\n" +

            "{historyBlock}\n\n" +

            "{feedbackBlock}\n\n" +

            "EXERCISE RULES:\n" +
            "- Generate EXACTLY 5 exercises\n" +
            "- Use ONLY exercise IDs from the catalog below\n" +
            "- Order: compound movements first, isolation last\n" +
            "- If includesCore is TRUE: 5th exercise MUST be a core exercise\n" +
            "- If includesCore is FALSE: do NOT include any core exercises\n" +
            "- NEVER select 'plank' as a core exercise — it is duration-based and not supported in the app. For core, choose from: dead-bug, mountain-climbers, russian-twist, leg-raises, bicycle-crunches, hanging-leg-raises, ab-wheel-rollout\n" +
            "- Do NOT repeat any exercise listed in recentExercises block\n\n" +

            "WEIGHT SUGGESTION RULES — FOLLOW EXACTLY:\n" +
            "Philosophy: Always suggest conservative weights. " +
            "Users can increase or decrease via the app. " +
            "It is better to start light than cause injury.\n\n" +

            "LATERAL RAISES — special rule for ALL levels and genders:\n" +
            "suggestedKg MUST NOT exceed 5kg. " +
            "This applies to ALL fitness levels without exception.\n\n" +

            "BEGINNER female:\n" +
            "  Dumbbell exercises: 2–4kg\n" +
            "  Cable machine: 2–5kg\n" +
            "  Machine (plates): 5–10kg\n" +
            "  Barbell/Smith machine: 5–10kg\n" +
            "  Bodyweight exercises: suggestedKg must be null\n\n" +

            "BEGINNER male:\n" +
            "  Dumbbell exercises: 4–6kg\n" +
            "  Cable machine: 5–8kg\n" +
            "  Machine (plates): 10–20kg\n" +
            "  Barbell/Smith machine: 10–20kg\n" +
            "  Bodyweight exercises: suggestedKg must be null\n\n" +

            "INTERMEDIATE female:\n" +
            "  Dumbbell exercises: 6–10kg\n" +
            "  Cable machine: 5–10kg\n" +
            "  Machine (plates): 15–30kg\n" +
            "  Barbell/Smith machine: 20–40kg\n" +
            "  Lateral raises: max 5kg\n\n" +

            "INTERMEDIATE male:\n" +
            "  Dumbbell exercises: 10–16kg\n" +
            "  Cable machine: 10–20kg\n" +
            "  Machine (plates): 25–40kg\n" +
            "  Barbell/Smith machine: 40–70kg\n" +
            "  Lateral raises: max 5kg\n\n" +

            "ADVANCED female:\n" +
            "  Dumbbell exercises: 10–16kg\n" +
            "  Cable machine: 10–20kg\n" +
            "  Machine (plates): 25–50kg\n" +
            "  Barbell/Smith machine: 30–60kg\n" +
            "  Lateral raises: max 5kg\n\n" +

            "ADVANCED male:\n" +
            "  Dumbbell exercises: 16–24kg\n" +
            "  Cable machine: 20–35kg\n" +
            "  Machine (plates): 40–70kg\n" +
            "  Barbell/Smith machine: 60–100kg\n" +
            "  Lateral raises: max 5kg\n\n" +

            "ALWAYS add to coachTip for week 1-2 beginners:\n" +
            "'If this feels too heavy or too light, " +
            "adjust the weight in the app — " +
            "these are starting suggestions only.'\n\n" +

            "HYPERTENSION — add to EVERY exercise coachTip:\n" +
            "'Exhale on exertion, never hold your breath.'\n\n" +

            "GAIN_WEIGHT goal — sessionNote MUST mention:\n" +
            "'Focus on progressive overload and " +
            "eating in a calorie surplus to support muscle growth.'\n\n" +

            "FAT_LOSS goal — sessionNote MUST mention:\n" +
            "'Keep rest periods short to maintain elevated " +
            "heart rate for maximum calorie burn.'\n\n" +

            "BUILD_MUSCLE goal — sessionNote MUST mention:\n" +
            "'Progressive overload is key — " +
            "aim to beat last session by at least 1 rep or 2.5kg.'\n\n" +

            "Round all weights to nearest real gym increment:\n" +
            "- Barbell: nearest 2.5kg (e.g. 20, 22.5, 25)\n" +
            "- Dumbbell: nearest 2kg (e.g. 4, 6, 8)\n" +
            "- Machine: nearest 5kg (e.g. 10, 15, 20)\n" +
            "- Cable: nearest 2.5kg (e.g. 2.5, 5, 7.5)\n" +

            "HEALTH CONDITION HARD RULES — NON NEGOTIABLE:\n" +
            "PREGNANCY:\n" +
            "- BANNED: deadlift, squat, bench-press, overhead-press, " +
            "push-press, plank, crunches, leg-raises, hanging-leg-raises\n" +
            "- ALLOWED: seated-cable-row, lat-pulldown, lateral-raises, " +
            "glute-bridge, seated-calf-raises, dumbbell-row, face-pulls\n" +
            "- coachTip on every exercise MUST mention breathing and safety\n" +
            "BACK PAIN:\n" +
            "- BANNED: deadlift, romanian-deadlift, barbell-row, " +
            "squat, good-mornings\n" +
            "- REPLACE WITH: leg-press, glute-bridge, seated-cable-row, " +
            "lat-pulldown, cable-flyes\n" +
            "KNEE ISSUES:\n" +
            "- BANNED: lunges, squat, box-jumps, leg-extension, " +
            "bulgarian-split-squat, step-ups\n" +
            "- REPLACE WITH: leg-press, lying-leg-curl, glute-bridge, " +
            "seated-calf-raises\n" +
            "SHOULDER INJURY:\n" +
            "- BANNED: overhead-press, push-press, upright-rows, " +
            "skull-crushers, arnold-press\n" +
            "- REPLACE WITH: cable-flyes, lateral-raises, " +
            "tricep-pushdowns, face-pulls\n" +
            "HYPERTENSION:\n" +
            "- BANNED: power-clean, push-press, heavy deadlift\n" +
            "- coachTip MUST say: exhale on exertion, no breath holding\n\n" +

            "EXERCISE CATALOG (use ONLY these IDs):\n" +
            "CHEST: bench-press, incline-db-press, db-flat-press, " +
            "smith-machine-bench, decline-bench-press, cable-flyes, " +
            "push-ups, knee-push-ups, incline-push-ups, dips, " +
            "cable-crossover, pec-deck\n" +
            "SHOULDERS: shoulder-press, overhead-press, arnold-press, " +
            "push-press, db-shoulder-press, lateral-raises, front-raises, " +
            "cable-lateral-raise, reverse-flyes, face-pulls\n" +
            "BACK: pull-ups, chin-ups, weighted-pull-ups, inverted-row, " +
            "lat-pulldown, barbell-row, dumbbell-row, seated-cable-row, " +
            "t-bar-row, deadlift, face-pulls, reverse-flyes\n" +
            "TRICEPS: tricep-pushdowns, close-grip-bench, skull-crushers, " +
            "tricep-overhead-extension, tricep-kickback\n" +
            "BICEPS: bicep-curl, barbell-curl, hammer-curl, " +
            "concentration-curl, chin-ups\n" +
            "LEGS: squat, goblet-squat, hack-squat, leg-press, lunges, " +
            "bulgarian-split-squat, step-ups, romanian-deadlift, " +
            "sumo-deadlift, leg-curl, lying-leg-curl, nordic-curl, " +
            "leg-extension, hip-thrust, glute-bridge, cable-kickback, " +
            "standing-calf-raises, seated-calf-raises, single-leg-calf-raise\n" +
            "CORE: plank, crunches, dead-bug, mountain-climbers, " +
            "russian-twist, leg-raises, bicycle-crunches, " +
            "hanging-leg-raises, ab-wheel-rollout, dragon-flag\n" +
            "FULL BODY: kettlebell-swing, burpees, box-jumps, power-clean\n\n" +

            "LENGTH RULES — STRICTLY ENFORCED:\n" +
            "- sessionNote: MAX 3 sentences. MUST reference recovery state " +
            "and/or feedback rating. Never generic.\n" +
            "- dayCoachTip: EXACTLY 1 sentence. Actionable. " +
            "Specific to today's session type.\n" +
            "- whyThisExercise: MAX 2 sentences per exercise.\n" +
            "- coachTip per exercise: EXACTLY 1 sentence.\n" +
            "- Each exercise MUST have a unique, exercise-specific coachTip. " +
            "Do NOT use generic tips like 'Exhale on exertion' or 'Focus on form'. " +
            "Instead, give a specific biomechanical cue for THAT exercise. " +
            "For example: Bench Press → 'Keep shoulder blades pinched together throughout', " +
            "Lateral Raises → 'Lead with pinky finger slightly higher than thumb'.\n" +
            "- cardioSuggestion reason: EXACTLY 1 sentence.\n\n" +
            "RETURN this exact JSON structure:\n" +
            "{\n" +
            "  \"exercises\": [\n" +
            "    {\n" +
            "      \"exerciseId\": \"bench-press\",\n" +
            "      \"exerciseName\": \"Bench Press\",\n" +
            "      \"sets\": 4,\n" +
            "      \"reps\": 10,\n" +
            "      \"restSeconds\": 90,\n" +
            "      \"suggestedKg\": 60.0,\n" +
            "      \"whyThisExercise\": \"Max 2 sentences specific to user goal, level and recovery\",\n" +
            "      \"coachTip\": \"1 actionable sentence only\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"sessionNote\": \"Max 3 sentences. Why today specifically — reference recovery state or feedback rating. Never generic.\",\n" +
            "  \"dayCoachTip\": \"1 sentence only. Actionable tip for today's session type.\",\n" +
            "  \"cardioSuggestion\": {\n" +
            "    \"type\": \"brisk_walk\",\n" +
            "    \"durationMins\": 20,\n" +
            "    \"reason\": \"1 sentence why\"\n" +
            "  }\n" +
            "}\n\n" +
            "Return ONLY valid JSON. No markdown. No explanation outside JSON.";
}
