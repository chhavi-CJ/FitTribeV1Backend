package com.fittribe.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.config.AiPrompts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standalone quality test — no Spring, no DB, no auth.
 * Calls OpenAI GPT-4o directly with prompts built from AiPrompts constants
 * and validates the exercise plan responses against business rules.
 *
 * Run: OPENAI_API_KEY=your-key ./mvnw test \
 *      -Dtest=PlanGenerationQualityTest \
 *      -Dsurefire.failIfNoSpecifiedTests=false
 */
public class PlanGenerationQualityTest {

    // ── Split template data (mirrors split_template_days seed) ────────

    record DayTemplate(
            String dayType,
            String dayLabel,
            List<String> muscleGroups,
            boolean includesCore,
            int estimatedMins,
            String cardioType,
            Integer cardioDurationMin
    ) {}

    static final Map<String, DayTemplate> TEMPLATES = Map.of(
            "upper_a",   new DayTemplate("upper_a",  "Upper A",
                    List.of("Chest","Shoulders","Triceps"),                false, 45, "brisk_walk",    10),
            "lower",     new DayTemplate("lower",    "Lower",
                    List.of("Quads","Hamstrings","Glutes"),                false, 50, "incline_walk",  10),
            "upper_b",   new DayTemplate("upper_b",  "Upper B",
                    List.of("Back","Biceps","Rear Delts"),                  true, 45, null,           null),
            "push_a",    new DayTemplate("push_a",   "Push A",
                    List.of("Chest","Front Delts","Triceps"),              false, 45, "brisk_walk",    10),
            "pull_a",    new DayTemplate("pull_a",   "Pull A",
                    List.of("Back","Rear Delts","Biceps"),                  true, 45, null,           null),
            "legs",      new DayTemplate("legs",     "Legs",
                    List.of("Quads","Hamstrings","Glutes","Calves"),       false, 50, "incline_walk",  10),
            "push_b",    new DayTemplate("push_b",   "Push B",
                    List.of("Upper Chest","Shoulders","Triceps"),          false, 45, "brisk_walk",    10),
            "pull_b",    new DayTemplate("pull_b",   "Pull B",
                    List.of("Back Width","Rear Delts","Biceps","Core"),     true, 45, null,           null),
            "full_body", new DayTemplate("full_body","Full Body",
                    List.of("Chest","Back","Shoulders","Legs","Arms","Core"), true, 55, "brisk_walk", 10),
            "lower_a",   new DayTemplate("lower_a",  "Lower A",
                    List.of("Quads","Hamstrings","Glutes"),                false, 50, "incline_walk",  10)
    );

    // ── Test personas ─────────────────────────────────────────────────

    record TestPersona(
            String name, String gender, String goal,
            String fitnessLevel, double weightKg,
            double heightCm, int weeklyGoal,
            List<String> healthConditions,
            int weekNumber,
            String dayType
    ) {}

    static final List<TestPersona> PERSONAS = List.of(
            new TestPersona("Riya",   "female", "FAT_LOSS",        "BEGINNER",     55, 160, 1, List.of(),                  1, "full_body"),
            new TestPersona("Priya",  "female", "FAT_LOSS",        "BEGINNER",     58, 162, 2, List.of("PCOS"),             1, "upper_a"),
            new TestPersona("Meera",  "female", "GENERAL_FITNESS", "BEGINNER",     45, 155, 3, List.of("Knee issues"),      1, "lower"),
            new TestPersona("Ananya", "female", "BUILD_MUSCLE",    "INTERMEDIATE", 65, 168, 4, List.of("Back pain"),        3, "upper_a"),
            new TestPersona("Kavya",  "female", "GAIN_WEIGHT",     "ADVANCED",     52, 160, 5, List.of("Shoulder injury"),  8, "push_a"),
            new TestPersona("Arjun",  "male",   "FAT_LOSS",        "BEGINNER",     68, 170, 6, List.of("Hypertension"),     1, "push_a"),
            new TestPersona("Rahul",  "male",   "BUILD_MUSCLE",    "INTERMEDIATE", 75, 175, 7, List.of("Back pain"),        5, "pull_a")
    );

    // ── Banned exercises per health condition ─────────────────────────

    static final Map<String, List<String>> BANNED = Map.of(
            "Back pain",       List.of("deadlift","romanian-deadlift","barbell-row","squat","good-mornings"),
            "Knee issues",     List.of("lunges","squat","box-jumps","leg-extension","bulgarian-split-squat","step-ups"),
            "Shoulder injury", List.of("overhead-press","push-press","skull-crushers","arnold-press","upright-rows"),
            "Hypertension",    List.of("power-clean","push-press")
    );

    // ── Core exercise IDs ─────────────────────────────────────────────

    static final Set<String> CORE_EXERCISES = Set.of(
            "plank","crunches","dead-bug","mountain-climbers","russian-twist",
            "leg-raises","bicycle-crunches","hanging-leg-raises","ab-wheel-rollout","dragon-flag"
    );

    // ── Weight limits per level/gender/equipment type ─────────────────

    record WeightLimit(String level, String gender, String equipType, double maxKg) {}

    static final List<WeightLimit> WEIGHT_LIMITS = List.of(
            new WeightLimit("BEGINNER",     "female", "cable",    5.0),
            new WeightLimit("BEGINNER",     "female", "machine",  5.0),
            new WeightLimit("BEGINNER",     "female", "dumbbell", 4.0),
            new WeightLimit("BEGINNER",     "female", "barbell",  10.0),
            new WeightLimit("BEGINNER",     "male",   "cable",    10.0),
            new WeightLimit("BEGINNER",     "male",   "machine",  10.0),
            new WeightLimit("BEGINNER",     "male",   "dumbbell", 8.0),
            new WeightLimit("INTERMEDIATE", "female", "dumbbell", 12.0),
            new WeightLimit("INTERMEDIATE", "female", "cable",    10.0),
            new WeightLimit("INTERMEDIATE", "male",   "dumbbell", 20.0),
            new WeightLimit("ADVANCED",     "female", "dumbbell", 20.0)
    );

    // ── Equipment keyword detection ───────────────────────────────────

    static String detectEquipType(String exerciseId, String equipment) {
        String id  = exerciseId  != null ? exerciseId.toLowerCase()  : "";
        String eq  = equipment   != null ? equipment.toLowerCase()   : "";
        if (eq.contains("dumbbell") || id.contains("-db-") || id.startsWith("db-") || id.contains("db-")) return "dumbbell";
        if (eq.contains("cable")   || id.contains("cable"))   return "cable";
        if (eq.contains("machine") || id.contains("machine") || id.contains("press") && id.contains("smith")) return "machine";
        if (eq.contains("barbell") || id.contains("barbell") || id.contains("bench-press") || id.contains("overhead-press")) return "barbell";
        return "other";
    }

    // ── ObjectMapper ──────────────────────────────────────────────────

    static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Main test ─────────────────────────────────────────────────────

    @Test
    void testPlanGenerationQuality() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("""
                    ⚠️  OPENAI_API_KEY not set — skipping all personas.

                    To run this test:
                      export OPENAI_API_KEY=sk-...
                      ./mvnw test -Dtest=PlanGenerationQualityTest -Dsurefire.failIfNoSpecifiedTests=false
                    """);
            return;
        }

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        Path outputDir = Paths.get("src/test/outputs");
        Files.createDirectories(outputDir);

        int totalChecks = 0;
        int totalPassed = 0;

        record PersonaResult(String name, int days, String level, int passed, int total) {}
        List<PersonaResult> results = new ArrayList<>();

        for (TestPersona persona : PERSONAS) {
            DayTemplate template = TEMPLATES.get(persona.dayType());
            if (template == null) {
                System.out.println("⚠️  Unknown dayType: " + persona.dayType() + " — skipping " + persona.name());
                continue;
            }

            System.out.println("═".repeat(55));
            System.out.printf("PERSONA: %s | %s %s | %s | %dd | week %d%n",
                    persona.name(), persona.fitnessLevel(), persona.gender(),
                    persona.goal(), persona.weeklyGoal(), persona.weekNumber());
            System.out.printf("DAY: %s → %s | includesCore: %b%n",
                    persona.dayType(),
                    String.join(", ", template.muscleGroups()),
                    template.includesCore());
            System.out.println("═".repeat(55));

            // Build prompt
            String healthStr = persona.healthConditions().isEmpty()
                    ? "None"
                    : String.join(", ", persona.healthConditions());

            String userPrompt = AiPrompts.DAILY_EXERCISE_USER
                    .replace("{name}",             persona.name())
                    .replace("{gender}",           persona.gender())
                    .replace("{weightKg}",         String.valueOf(persona.weightKg()))
                    .replace("{heightCm}",         String.valueOf(persona.heightCm()))
                    .replace("{fitnessLevel}",     persona.fitnessLevel())
                    .replace("{goal}",             persona.goal())
                    .replace("{weekNumber}",       String.valueOf(persona.weekNumber()))
                    .replace("{healthConditions}", healthStr)
                    .replace("{aiContext}",            "")
                    .replace("{fitnessSummaryBlock}", "")
                    .replace("{dayLabel}",         template.dayLabel())
                    .replace("{muscleGroups}",     String.join(", ", template.muscleGroups()))
                    .replace("{includesCore}",     String.valueOf(template.includesCore()))
                    .replace("{estimatedMins}",    String.valueOf(template.estimatedMins()))
                    .replace("{guidanceText}",     "")
                    .replace("{recoveryBlock}",    "RECOVERY STATUS: No recent sessions — all muscle groups fully recovered.")
                    .replace("{historyBlock}",     "HISTORY: No training history — first session. Use conservative starting weights.")
                    .replace("{feedbackBlock}",    "");

            // Call OpenAI
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "gpt-4o");
            requestBody.put("max_tokens", 3000);
            requestBody.put("temperature", 0.3);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", AiPrompts.DAILY_EXERCISE_SYSTEM),
                    Map.of("role", "user",   "content", userPrompt)
            ));

            String requestJson = MAPPER.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> httpResponse;
            try {
                httpResponse = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                System.out.println("❌ HTTP error for " + persona.name() + ": " + e.getMessage());
                continue;
            }

            if (httpResponse.statusCode() != 200) {
                System.out.println("❌ OpenAI returned " + httpResponse.statusCode() + " for " + persona.name());
                System.out.println("   " + httpResponse.body().substring(0, Math.min(200, httpResponse.body().length())));
                continue;
            }

            // Parse OpenAI envelope
            Map<String, Object> apiResp = MAPPER.readValue(
                    httpResponse.body(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) apiResp.get("choices");
            if (choices == null || choices.isEmpty()) {
                System.out.println("❌ No choices in response for " + persona.name());
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) msg.get("content");

            // Strip markdown fences
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```\\s*$", "").trim();
            }

            // Save raw output
            Path outFile = outputDir.resolve(persona.name().toLowerCase() + "-" + persona.dayType() + "-gpt4o.json");
            Files.writeString(outFile, MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(MAPPER.readValue(json, new TypeReference<>() {})));

            // Parse plan
            Map<String, Object> plan;
            try {
                plan = MAPPER.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                System.out.println("❌ JSON parse failed for " + persona.name() + ": " + e.getMessage());
                System.out.println("   Raw: " + json.substring(0, Math.min(300, json.length())));
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exercises = (List<Map<String, Object>>) plan.get("exercises");
            if (exercises == null) exercises = List.of();

            String sessionNote  = (String) plan.get("sessionNote");
            String dayCoachTip  = (String) plan.get("dayCoachTip");
            Object cardioSug    = plan.get("cardioSuggestion");

            int passed = 0;
            int checks = 8;

            // Check a — exercise count
            boolean countOk = exercises.size() == 5;
            System.out.printf("%s Exercise count: %d/5%n", countOk ? "✅" : "❌", exercises.size());
            if (countOk) passed++;

            // Check b — core rule
            long coreCount = exercises.stream()
                    .map(e -> (String) e.get("exerciseId"))
                    .filter(Objects::nonNull)
                    .filter(CORE_EXERCISES::contains)
                    .count();

            boolean coreOk;
            if (!template.includesCore()) {
                coreOk = coreCount == 0;
                System.out.printf("%s No core exercises (includesCore=false) — found %d%n",
                        coreOk ? "✅" : "❌", coreCount);
            } else {
                coreOk = coreCount == 1;
                System.out.printf("%s Exactly 1 core exercise (includesCore=true) — found %d%n",
                        coreOk ? "✅" : "❌", coreCount);
            }
            if (coreOk) passed++;

            // Collect banned IDs for this persona
            List<String> bannedIds = persona.healthConditions().stream()
                    .flatMap(c -> BANNED.getOrDefault(c, List.of()).stream())
                    .distinct()
                    .collect(Collectors.toList());

            // Check c — no banned in main list
            List<String> mainBanned = exercises.stream()
                    .map(e -> (String) e.get("exerciseId"))
                    .filter(Objects::nonNull)
                    .filter(bannedIds::contains)
                    .collect(Collectors.toList());
            boolean noBannedMain = mainBanned.isEmpty();
            System.out.printf("%s No banned exercises in main list%s%n",
                    noBannedMain ? "✅" : "❌",
                    noBannedMain ? "" : ": " + String.join(", ", mainBanned));
            if (noBannedMain) passed++;

            // Check d — no banned in swapAlternatives
            List<String> swapBanned = new ArrayList<>();
            for (Map<String, Object> ex : exercises) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> swaps =
                        (List<Map<String, Object>>) ex.getOrDefault("swapAlternatives", List.of());
                for (Map<String, Object> swap : swaps) {
                    String swapId = (String) swap.get("exerciseId");
                    if (swapId != null && bannedIds.contains(swapId)) {
                        swapBanned.add(swapId + " in " + ex.get("exerciseId") + " alternatives");
                    }
                }
            }
            boolean noBannedSwaps = swapBanned.isEmpty();
            System.out.printf("%s No banned exercises in swapAlternatives%s%n",
                    noBannedSwaps ? "✅" : "❌",
                    noBannedSwaps ? "" : ": " + String.join(", ", swapBanned));
            if (noBannedSwaps) passed++;

            // Check e — weight ranges
            List<String> weightIssues = new ArrayList<>();
            for (Map<String, Object> ex : exercises) {
                String exId    = (String) ex.get("exerciseId");
                Object kgRaw   = ex.get("suggestedKg");
                String equip   = (String) ex.getOrDefault("equipment", "");
                if (kgRaw == null) continue; // bodyweight — skip
                double kg;
                try { kg = ((Number) kgRaw).doubleValue(); } catch (Exception e) { continue; }

                String equipType = detectEquipType(exId, equip);
                for (WeightLimit limit : WEIGHT_LIMITS) {
                    if (limit.level().equals(persona.fitnessLevel())
                            && limit.gender().equals(persona.gender())
                            && limit.equipType().equals(equipType)
                            && kg > limit.maxKg()) {
                        weightIssues.add(String.format("%s %.1fkg (max %.1f for %s %s %s)",
                                exId, kg, limit.maxKg(),
                                persona.fitnessLevel(), persona.gender(), equipType));
                    }
                }
            }
            boolean weightsOk = weightIssues.isEmpty();
            if (weightsOk) {
                System.out.println("✅ Weights within range for " + persona.fitnessLevel() + " " + persona.gender());
            } else {
                for (String issue : weightIssues) {
                    System.out.println("❌ Weight too heavy: " + issue);
                }
            }
            if (weightsOk) passed++;

            // Check f — sessionNote
            boolean noteOk = sessionNote != null && sessionNote.length() > 20;
            System.out.printf("%s sessionNote present (%d chars)%n",
                    noteOk ? "✅" : "❌",
                    sessionNote != null ? sessionNote.length() : 0);
            if (noteOk) passed++;

            // Check g — dayCoachTip
            boolean tipOk = dayCoachTip != null && dayCoachTip.length() > 10;
            System.out.printf("%s dayCoachTip present (%d chars)%n",
                    tipOk ? "✅" : "❌",
                    dayCoachTip != null ? dayCoachTip.length() : 0);
            if (tipOk) passed++;

            // Check h — cardioSuggestion
            boolean cardioOk = cardioSug != null;
            System.out.printf("%s cardioSuggestion present%n", cardioOk ? "✅" : "❌");
            if (cardioOk) passed++;

            // Score
            String status = passed == checks ? "✅" : passed >= checks - 2 ? "⚠️" : "❌";
            System.out.printf("SCORE: %d/%d checks passed %s%n%n", passed, checks, status);

            // Print exercises
            System.out.println("Exercises generated:");
            for (int i = 0; i < exercises.size(); i++) {
                Map<String, Object> ex = exercises.get(i);
                String exId    = (String) ex.getOrDefault("exerciseId",   "?");
                String exName  = (String) ex.getOrDefault("exerciseName", "?");
                String muscle  = (String) ex.getOrDefault("muscleGroup",  "?");
                Object sets    = ex.getOrDefault("sets", "?");
                Object reps    = ex.getOrDefault("reps", "?");
                Object kgRaw   = ex.get("suggestedKg");
                String kgStr   = kgRaw != null ? ((Number) kgRaw).doubleValue() + "kg" : "bodyweight";

                // flag if this exercise has a weight issue
                boolean thisExHeavy = weightIssues.stream().anyMatch(w -> w.startsWith(exId));
                System.out.printf("  %d. %s | %s | %s | %sx%s | %s%s%n",
                        i + 1, exId, exName, muscle, sets, reps, kgStr,
                        thisExHeavy ? " ❌ too heavy" : "");
            }

            System.out.println("─".repeat(55));

            results.add(new PersonaResult(
                    persona.name() + " (" + persona.weeklyGoal() + "d)",
                    persona.weeklyGoal(), persona.fitnessLevel(), passed, checks));
            totalPassed += passed;
            totalChecks += checks;
        }

        // Final summary table
        System.out.println();
        System.out.println("═".repeat(63));
        System.out.println("FINAL SUMMARY — GPT-4o Quality Test");
        System.out.println("═".repeat(63));
        System.out.printf("%-20s %-5s %-12s %-8s %s%n",
                "Persona", "Days", "Level", "Score", "Status");
        System.out.println("─".repeat(63));

        for (PersonaResult r : results) {
            String status = r.passed() == r.total() ? "✅"
                    : r.passed() >= r.total() - 2 ? "⚠️" : "❌";
            System.out.printf("%-20s %-5d %-12s %-8s %s%n",
                    r.name(), r.days(),
                    r.level().substring(0, Math.min(8, r.level().length())),
                    r.passed() + "/" + r.total(),
                    status);
        }

        System.out.println("─".repeat(63));
        int pct = totalChecks > 0 ? (totalPassed * 100 / totalChecks) : 0;
        System.out.printf("TOTAL: %d/%d checks passed (%d%%)%n", totalPassed, totalChecks, pct);
        System.out.println("═".repeat(63));
    }
}
