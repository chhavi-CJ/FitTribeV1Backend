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
            "RETURN a JSON plan with this structure:\n" +
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
            "          \"exerciseName\": \"Barbell Bench Press\",\n" +
            "          \"sets\": 3,\n" +
            "          \"reps\": 8,\n" +
            "          \"restSeconds\": 90,\n" +
            "          \"suggestedKg\": 35.0,\n" +
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
