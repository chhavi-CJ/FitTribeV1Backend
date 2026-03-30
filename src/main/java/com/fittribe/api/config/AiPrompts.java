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
            "You are an expert fitness coach inside FitTribe, an Indian gym app. " +
            "Be specific — never generic. Always reference their actual numbers. " +
            "Max 2-3 sentences. No emojis.";

    public static final String DAILY_INSIGHT_USER =
            "USER PROFILE:\n" +
            "Name: {name} | Gender: {gender} | Weight: {weightKg}kg\n" +
            "Level: {fitnessLevel} | Goal: {goal}\n\n" +
            "{healthConditions}" +
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
            "WEEKLY SPLIT RULES:\n" +
            "weeklyGoal = 3: Push A, Pull A, Leg Day\n" +
            "weeklyGoal = 4: Push A, Pull A, Leg Day, Full Body\n" +
            "weeklyGoal = 5: Push A, Pull A, Legs A, Upper Body, Lower Body\n" +
            "weeklyGoal = 6: Push A, Pull A, Legs A, Push B, Pull B, Legs B\n" +
            "Always include at least 1 core exercise per day.\n" +
            "Core exercises to pick from: plank, dead-bug, mountain-climbers, russian-twist, leg-raises, bicycle-crunches\n\n" +
            "EXERCISE RULES:\n" +
            "- MANDATORY: include EXACTLY 5 exercises per day — no more, no fewer\n" +
            "- Always include 1 core exercise per day from: plank, dead-bug, mountain-climbers, russian-twist, leg-raises, bicycle-crunches, hanging-leg-raises, ab-wheel-rollout\n" +
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
}
