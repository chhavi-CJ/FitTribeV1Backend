package com.fittribe.api.bonus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.healthcondition.HealthCondition;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.RecoveryGateService;
import com.fittribe.api.service.RecoveryGateService.RecoveryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrator for bonus session generation. This is the skeleton — it
 * calls every dependency (RecoveryGate, Resolver, PromptBuilder) but
 * returns a STUB response with archetype-default exercises instead of
 * making an actual AI call.
 *
 * Step 5b-2 replaces the stub with a real OpenAI call + catalog validation.
 * Step 5b-3 wires this into PlanController as GET /plan/today?bonus=true.
 *
 * Flow:
 *   1. Fetch user
 *   2. Compute recovery state
 *   3. Count bonuses completed this week (ISO Mon-Sun)
 *   4. Count bonuses generated today (to compute next bonus_number)
 *   5. Resolve archetype
 *   6. Build prompt (unused in 5b-1 but validates no NPE in assembly)
 *   7. Generate stub exercises for the archetype
 *   8. Persist to bonus_session_generated
 *   9. Return response map
 */
@Service
public class BonusSessionService {

    private static final Logger log = LoggerFactory.getLogger(BonusSessionService.class);

    private final UserRepository             userRepo;
    private final BonusSessionRepository     bonusRepo;
    private final RecoveryGateService        recoveryGate;
    private final SessionArchetypeResolver   resolver;
    private final BonusSessionPromptBuilder  promptBuilder;
    private final ObjectMapper               mapper;

    public BonusSessionService(UserRepository userRepo,
                                BonusSessionRepository bonusRepo,
                                RecoveryGateService recoveryGate,
                                SessionArchetypeResolver resolver,
                                BonusSessionPromptBuilder promptBuilder,
                                ObjectMapper mapper) {
        this.userRepo      = userRepo;
        this.bonusRepo     = bonusRepo;
        this.recoveryGate  = recoveryGate;
        this.resolver      = resolver;
        this.promptBuilder = promptBuilder;
        this.mapper        = mapper;
    }

    /**
     * Generate a bonus session for the given user. Stub implementation —
     * returns archetype-default exercises without calling AI.
     *
     * @return response map with archetype, exercises, and notes
     */
    public Map<String, Object> generate(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        Instant now        = Instant.now();
        LocalDate today    = LocalDate.now(ZoneOffset.UTC);
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

        Archetype archetype    = resolver.resolve(input);
        String    rationale    = buildRationale(archetype, canonical, recoveryMap, bonusesThisWeek);

        // Assemble prompt to catch any NPE/format issue in 5b-1 — discard the string
        String prompt = promptBuilder.build(user, archetype, rationale, recoveryMap, bonusesThisWeek);
        log.debug("Bonus prompt built for user={} ({}char), archetype={}",
                userId, prompt.length(), archetype);

        // STUB: generate default exercises per archetype. Replaced in 5b-2 by AI call.
        List<Map<String, Object>> exercises = stubExercisesForArchetype(archetype);
        String sessionNote  = "Weekly goal hit — nice work. This bonus is for momentum, not volume.";
        String dayCoachTip  = "Move with intent, not intensity.";

        int bonusNumber = bonusesToday + 1;
        BonusSessionGenerated entity = new BonusSessionGenerated();
        entity.setId(new BonusSessionGeneratedId(userId, today, bonusNumber));
        entity.setArchetype(archetype.name());
        entity.setArchetypeRationale(rationale);
        try {
            entity.setExercises(mapper.writeValueAsString(exercises));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize bonus exercises", e);
        }
        entity.setSessionNote(sessionNote);
        entity.setDayCoachTip(dayCoachTip);
        bonusRepo.save(entity);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",       "GENERATED");
        response.put("archetype",    archetype.name());
        response.put("rationale",    rationale);
        response.put("bonusNumber",  bonusNumber);
        response.put("exercises",    exercises);
        response.put("sessionNote",  sessionNote);
        response.put("dayCoachTip",  dayCoachTip);
        return response;
    }

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
                                   Map<String, RecoveryState> rec,
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

    /**
     * Placeholder exercises per archetype. Replaced in 5b-2 by real AI.
     * Returns 4 exercise stubs — just enough to exercise the storage layer.
     */
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
            ex.put("whyThisExercise",  "Stub — will be AI-generated in Step 5b-2.");
            ex.put("coachTip",         "Stub tip.");
            out.add(ex);
        }
        return out;
    }
}
