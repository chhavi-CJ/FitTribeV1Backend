package com.fittribe.api.bonus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.healthcondition.HealthCondition;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.RecoveryGateService;
import com.fittribe.api.service.RecoveryGateService.RecoveryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator for bonus session generation.
 *
 * Flow:
 *   1. Fetch user
 *   2. Compute recovery state
 *   3. Count bonuses this week + today (for bonus_number sequencing)
 *   4. Resolve archetype (safety rules applied inside resolver)
 *   5. Build prompt
 *   6. Call OpenAI
 *   7. Parse + catalog-validate the response
 *   8. Fallback to archetype-default stubs on any failure
 *   9. Persist to bonus_session_generated
 *  10. Return response
 *
 * Response status values:
 *   GENERATED          — AI call succeeded and all exercises validated against catalog
 *   GENERATED_FALLBACK — AI unavailable, malformed, or returned invalid exercise IDs
 *                        (user still gets a real session using archetype defaults)
 */
@Service
public class BonusSessionService {

    private static final Logger log = LoggerFactory.getLogger(BonusSessionService.class);

    @Value("${openai.api-key:}")
    private String openAiKey;

    private final UserRepository            userRepo;
    private final BonusSessionRepository    bonusRepo;
    private final ExerciseRepository        exerciseRepo;
    private final RecoveryGateService       recoveryGate;
    private final SessionArchetypeResolver  resolver;
    private final BonusSessionPromptBuilder promptBuilder;
    private final ObjectMapper              mapper;
    private final RestTemplate              restTemplate;

    @Autowired
    public BonusSessionService(UserRepository userRepo,
                                BonusSessionRepository bonusRepo,
                                ExerciseRepository exerciseRepo,
                                RecoveryGateService recoveryGate,
                                SessionArchetypeResolver resolver,
                                BonusSessionPromptBuilder promptBuilder,
                                ObjectMapper mapper) {
        this.userRepo      = userRepo;
        this.bonusRepo     = bonusRepo;
        this.exerciseRepo  = exerciseRepo;
        this.recoveryGate  = recoveryGate;
        this.resolver      = resolver;
        this.promptBuilder = promptBuilder;
        this.mapper        = mapper;
        this.restTemplate  = new RestTemplate();
    }

    // Package-private ctor for tests — injects a mock RestTemplate
    BonusSessionService(UserRepository userRepo,
                        BonusSessionRepository bonusRepo,
                        ExerciseRepository exerciseRepo,
                        RecoveryGateService recoveryGate,
                        SessionArchetypeResolver resolver,
                        BonusSessionPromptBuilder promptBuilder,
                        ObjectMapper mapper,
                        RestTemplate restTemplate,
                        String openAiKey) {
        this.userRepo      = userRepo;
        this.bonusRepo     = bonusRepo;
        this.exerciseRepo  = exerciseRepo;
        this.recoveryGate  = recoveryGate;
        this.resolver      = resolver;
        this.promptBuilder = promptBuilder;
        this.mapper        = mapper;
        this.restTemplate  = restTemplate;
        this.openAiKey     = openAiKey;
    }

    public Map<String, Object> generate(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        Instant   now       = Instant.now();
        LocalDate today     = LocalDate.now(ZoneOffset.UTC);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd   = weekStart.plusDays(6);

        Map<String, RecoveryState> recoveryMap = recoveryGate.computeRecoveryState(userId, now);
        int bonusesThisWeek = bonusRepo.countByIdUserIdAndIdDateBetween(userId, weekStart, weekEnd);
        int bonusesToday    = bonusRepo.findByIdUserIdAndIdDate(userId, today).size();

        Set<HealthCondition> canonical = parseHealthConditions(user.getHealthConditions());

        var input = new SessionArchetypeResolver.ResolverInput(
                user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 3,
                user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE",
                canonical,
                user.getAiContext(),
                recoveryMap,
                bonusesThisWeek);

        Archetype archetype = resolver.resolve(input);
        String    rationale = buildRationale(archetype, canonical, bonusesThisWeek);

        String prompt = promptBuilder.build(user, archetype, rationale, recoveryMap, bonusesThisWeek);

        // Try AI first, fall back to stubs on any problem
        ParsedAiResponse parsed = tryAiCall(prompt);

        boolean usedFallback = parsed == null || !isCatalogValid(parsed.exercises);
        List<Map<String, Object>> finalExercises;
        String sessionNote;
        String dayCoachTip;
        String finalRationale;
        String status;

        if (usedFallback) {
            finalExercises = stubExercisesForArchetype(archetype);
            sessionNote    = "Weekly goal hit — nice work. This bonus is for momentum, not volume.";
            dayCoachTip    = "Move with intent, not intensity.";
            finalRationale = "[FALLBACK] " + rationale;
            status         = "GENERATED_FALLBACK";
            log.info("Bonus session FALLBACK for user={} archetype={} reason={}",
                    userId, archetype,
                    parsed == null ? "ai_unavailable_or_malformed" : "invalid_catalog_ids");
        } else {
            finalExercises = parsed.exercises;
            sessionNote    = parsed.sessionNote != null ? parsed.sessionNote
                    : "Weekly goal hit — nice work.";
            dayCoachTip    = parsed.dayCoachTip != null ? parsed.dayCoachTip
                    : "Move with intent.";
            finalRationale = rationale;
            status         = "GENERATED";
        }

        int bonusNumber = bonusesToday + 1;
        BonusSessionGenerated entity = new BonusSessionGenerated();
        entity.setId(new BonusSessionGeneratedId(userId, today, bonusNumber));
        entity.setArchetype(archetype.name());
        entity.setArchetypeRationale(finalRationale);
        try {
            entity.setExercises(mapper.writeValueAsString(finalExercises));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize bonus exercises", e);
        }
        entity.setSessionNote(sessionNote);
        entity.setDayCoachTip(dayCoachTip);
        bonusRepo.save(entity);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",       status);
        response.put("archetype",    archetype.name());
        response.put("rationale",    finalRationale);
        response.put("bonusNumber",  bonusNumber);
        response.put("exercises",    finalExercises);
        response.put("sessionNote",  sessionNote);
        response.put("dayCoachTip",  dayCoachTip);
        return response;
    }

    // ── AI call ─────────────────────────────────────────────────────

    /**
     * Call OpenAI and parse the JSON response. Returns null on any failure
     * (no key, HTTP error, malformed JSON). Caller treats null as fallback signal.
     *
     * TODO(cleanup): extract shared chat caller to AiService so PlanService
     * and BonusSessionService don't duplicate this HTTP boilerplate.
     */
    private ParsedAiResponse tryAiCall(String userPrompt) {
        if (openAiKey == null || openAiKey.isBlank()) {
            log.debug("Bonus AI call skipped — no openAiKey configured");
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 2000);
            body.put("temperature", 0.3);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", AiPrompts.BONUS_EXERCISE_SYSTEM),
                    Map.of("role", "user",   "content", userPrompt)));

            String jsonBody = mapper.writeValueAsString(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiKey);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    new HttpEntity<>(jsonBody, headers), Map.class);
            if (resp == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            String content = msg != null ? (String) msg.get("content") : null;
            if (content == null) return null;

            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "")
                           .replaceAll("```$", "")
                           .trim();
            }

            Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exercises =
                    (List<Map<String, Object>>) parsed.get("exercises");
            if (exercises == null || exercises.isEmpty()) return null;

            return new ParsedAiResponse(
                    exercises,
                    (String) parsed.get("sessionNote"),
                    (String) parsed.get("dayCoachTip"));

        } catch (Exception e) {
            log.warn("Bonus OpenAI call/parse failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verify every exerciseId in the AI response exists in the catalog.
     * Returns false if any ID is null, blank, or unknown.
     */
    private boolean isCatalogValid(List<Map<String, Object>> exercises) {
        if (exercises == null || exercises.isEmpty()) return false;
        Set<String> knownIds = exerciseRepo.findAll().stream()
                .map(Exercise::getId)
                .collect(Collectors.toSet());
        for (Map<String, Object> ex : exercises) {
            String id = (String) ex.get("exerciseId");
            if (id == null || id.isBlank() || !knownIds.contains(id)) {
                log.warn("Bonus AI returned unknown exerciseId: '{}'", id);
                return false;
            }
        }
        return true;
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Set<HealthCondition> parseHealthConditions(String[] raw) {
        if (raw == null || raw.length == 0) return Set.of();
        return Arrays.stream(raw)
                .map(HealthCondition::fromCanonical)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    private String buildRationale(Archetype archetype,
                                   Set<HealthCondition> conditions,
                                   int bonusesThisWeek) {
        if (conditions.contains(HealthCondition.PREGNANCY))
            return "Safety-first archetype for pregnancy.";
        if (conditions.contains(HealthCondition.HEART_CONDITION))
            return "Safety-first archetype for heart condition.";
        if (conditions.contains(HealthCondition.POSTNATAL))
            return "Safety-first archetype for postnatal recovery.";
        if (bonusesThisWeek >= 2)
            return "Gentler archetype — already " + bonusesThisWeek + " bonuses this week.";
        return "Matched to " + archetype.name() + " based on recovery state.";
    }

    private List<Map<String, Object>> stubExercisesForArchetype(Archetype archetype) {
        List<String> ids = switch (archetype) {
            case PUSH              -> List.of("bench-press", "shoulder-press", "tricep-pushdowns", "lateral-raises");
            case PULL              -> List.of("lat-pulldown", "seated-cable-row", "bicep-curl", "face-pulls");
            case LEGS              -> List.of("leg-press", "leg-curl", "glute-bridge", "standing-calf-raises");
            case WEAK_POINT_FOCUS  -> List.of("cable-flyes", "hammer-curl", "tricep-kickback", "lateral-raises");
            case ACCESSORY_CORE    -> List.of("bicep-curl", "face-pulls", "dead-bug", "russian-twist");
        };
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> ex = new LinkedHashMap<>();
            ex.put("exerciseId",       id);
            ex.put("sets",             3);
            ex.put("reps",             12);
            ex.put("restSeconds",      60);
            ex.put("suggestedKg",      null);
            ex.put("whyThisExercise",  "Default suggestion — AI unavailable.");
            ex.put("coachTip",         "Focus on form and controlled tempo.");
            out.add(ex);
        }
        return out;
    }

    // ── Inner DTO ───────────────────────────────────────────────────

    private record ParsedAiResponse(
            List<Map<String, Object>> exercises,
            String sessionNote,
            String dayCoachTip) {}
}
