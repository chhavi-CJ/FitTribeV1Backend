package com.fittribe.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.fitnesssummary.FitnessSummary;
import com.fittribe.api.fitnesssummary.FitnessSummaryService;
import com.fittribe.api.util.MuscleGroupUtil;
import com.fittribe.api.util.PromptSanitiser;
import static com.fittribe.api.util.Zones.APP_ZONE;
import com.fittribe.api.util.Zones;
import com.fittribe.api.entity.AiInsight;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserPlan;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.AiInsightRepository;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.SplitTemplateDay;
import com.fittribe.api.repository.SessionFeedbackRepository;
import com.fittribe.api.repository.SplitTemplateDayRepository;
import com.fittribe.api.entity.DailyPlanGenerated;
import com.fittribe.api.entity.DailyPlanGeneratedId;
import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.entity.UserDayStatusId;
import com.fittribe.api.repository.DailyPlanGeneratedRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserPlanRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    // ── Exercise-count constants ─────────────────────────────────────────
    private static final int PER_MUSCLE_FULL_BODY = 2;
    private static final int PER_MUSCLE_OTHER     = 3;

    private int perMuscleCount(String dayType) {
        if (dayType == null) return PER_MUSCLE_OTHER;
        return dayType.toLowerCase().contains("full") ? PER_MUSCLE_FULL_BODY : PER_MUSCLE_OTHER;
    }

    /** Maps common AI-invented exerciseIds to the canonical DB id. */
    private static final Map<String, String> EXERCISE_ID_ALIASES = Map.ofEntries(
            Map.entry("tricep-dips",                "dips"),
            Map.entry("dumbbell-shoulder-press",    "db-shoulder-press"),
            Map.entry("dumbbell-press",             "db-flat-press"),
            Map.entry("bodyweight-row",             "inverted-row"),
            Map.entry("bent-over-row",              "barbell-row"),
            Map.entry("barbell-bent-over-row",      "barbell-row"),
            Map.entry("bicep-curls",                "bicep-curl"),
            Map.entry("hammer-curls",               "hammer-curl"),
            Map.entry("cable-fly",                  "cable-flyes"),
            Map.entry("cable-flies",                "cable-flyes"),
            Map.entry("chest-fly",                  "pec-deck"),
            Map.entry("chest-flyes",                "cable-flyes"),
            Map.entry("leg-extensions",             "leg-extension"),
            Map.entry("tricep-kickbacks",           "tricep-kickback"),
            Map.entry("shoulder-lateral-raise",     "lateral-raises"),
            Map.entry("db-lateral-raise",           "lateral-raises"),
            Map.entry("cable-lateral-raises",       "cable-lateral-raise"),
            Map.entry("overhead-tricep-extension",  "tricep-overhead-extension"),
            Map.entry("lying-tricep-extension",     "skull-crushers"),
            Map.entry("seated-row",                 "seated-cable-row"),
            Map.entry("cable-row",                  "seated-cable-row"),
            Map.entry("db-row",                     "dumbbell-row"),
            Map.entry("one-arm-row",                "dumbbell-row"),
            Map.entry("single-arm-row",             "dumbbell-row"),
            Map.entry("pull-up",                    "pull-ups"),
            Map.entry("chin-up",                    "chin-ups"),
            Map.entry("hip-thrusts",                "hip-thrust"),
            Map.entry("glute-bridges",              "glute-bridge"),
            Map.entry("calf-raises",                "standing-calf-raises"),
            Map.entry("standing-calf-raise",        "standing-calf-raises"),
            Map.entry("leg-raise",                  "leg-raises"),
            Map.entry("hanging-leg-raise",          "hanging-leg-raises"),
            Map.entry("russian-twists",             "russian-twist"),
            Map.entry("mountain-climber",           "mountain-climbers")
    );

    @Value("${openai.api-key:}")
    private String openAiKey;

    private final UserRepository              userRepo;
    private final UserPlanRepository          planRepo;
    private final ExerciseRepository          exerciseRepo;
    private final WorkoutSessionRepository    sessionRepo;
    private final SetLogRepository            setLogRepo;
    private final AiInsightRepository         insightRepo;
    private final SessionFeedbackRepository   feedbackRepo;
    private final SplitTemplateDayRepository  splitTemplateDayRepo;
    private final DailyPlanGeneratedRepository dailyPlanRepo;
    private final UserDayStatusRepository      dayStatusRepo;
    private final FitnessSummaryService        fitnessSummaryService;
    private final PlanHistoryService           planHistoryService;
    private final ObjectMapper                mapper;
    private final FeedEventWriter             feedEventWriter;
    private final RestTemplate                restTemplate = new RestTemplate();

    public PlanService(UserRepository userRepo,
                       UserPlanRepository planRepo,
                       ExerciseRepository exerciseRepo,
                       WorkoutSessionRepository sessionRepo,
                       SetLogRepository setLogRepo,
                       AiInsightRepository insightRepo,
                       SessionFeedbackRepository feedbackRepo,
                       SplitTemplateDayRepository splitTemplateDayRepo,
                       DailyPlanGeneratedRepository dailyPlanRepo,
                       UserDayStatusRepository dayStatusRepo,
                       FitnessSummaryService fitnessSummaryService,
                       PlanHistoryService planHistoryService,
                       ObjectMapper mapper,
                       FeedEventWriter feedEventWriter) {
        this.userRepo             = userRepo;
        this.planRepo             = planRepo;
        this.exerciseRepo         = exerciseRepo;
        this.sessionRepo          = sessionRepo;
        this.setLogRepo           = setLogRepo;
        this.insightRepo          = insightRepo;
        this.feedbackRepo         = feedbackRepo;
        this.splitTemplateDayRepo = splitTemplateDayRepo;
        this.dailyPlanRepo        = dailyPlanRepo;
        this.dayStatusRepo        = dayStatusRepo;
        this.fitnessSummaryService = fitnessSummaryService;
        this.planHistoryService   = planHistoryService;
        this.mapper               = mapper;
        this.feedEventWriter      = feedEventWriter;
    }

    // ── Public API ────────────────────────────────────────────────────

    @Transactional
    public UserPlan generatePlan(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        LocalDate monday = LocalDate.now(APP_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekNumber = weekNumberFor(user, monday);

        // Idempotent — return existing plan if already generated this week
        Optional<UserPlan> existing = planRepo.findByUserIdAndWeekStartDate(userId, monday);
        if (existing.isPresent()) return existing.get();

        String level     = user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE";
        int weeklyGoal   = user.getWeeklyGoal()   != null ? user.getWeeklyGoal()   : 4;

        // Fetch split structure from DB — no AI call needed here
        List<SplitTemplateDay> splitTemplate = splitTemplateDayRepo
                .findByDaysPerWeekAndFitnessLevelOrderByDayNumber(weeklyGoal, level.toUpperCase());
        if (splitTemplate.isEmpty()) {
            splitTemplate = splitTemplateDayRepo
                    .findByDaysPerWeekAndFitnessLevelOrderByDayNumber(weeklyGoal, "INTERMEDIATE");
        }

        // Build days from split template — structure only, no exercises
        List<Map<String, Object>> days = splitTemplate.stream().map(td -> {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("dayNumber",       td.getDayNumber());
            day.put("dayLabel",        td.getDayLabel());
            day.put("dayType",         td.getDayType());
            day.put("muscleGroups",    td.getMuscleGroups());
            day.put("includesCore",    td.isIncludesCore());
            day.put("guidanceText",    td.getGuidanceText());
            day.put("cardioType",      td.getCardioType());
            day.put("cardioDurationMin", td.getCardioDurationMin());
            day.put("estimatedMins",   td.getEstimatedMins());
            return day;
        }).collect(Collectors.toList());

        try {
            UserPlan plan = new UserPlan();
            plan.setUserId(userId);
            plan.setWeekStartDate(monday);
            plan.setWeekNumber(weekNumber);
            plan.setDays(mapper.writeValueAsString(days));
            plan.setAiRationale(null);
            return planRepo.save(plan);
        } catch (Exception e) {
            log.error("Failed to serialize plan: {}", e.getMessage());
            throw new RuntimeException("Plan generation failed", e);
        }
    }

    public Map<String, Object> getTodaysPlan(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        LocalDate today = Zones.fitnessDayNow();
        LocalDate monday = today
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        UserPlan plan = planRepo.findByUserIdAndWeekStartDate(userId, monday)
                .orElseGet(() -> generatePlan(userId));

        int weeklyGoal = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;
        int completedThisWeek = sessionRepo.countCompletedThisWeekByStartedAt(userId);

        // Collect user day status — included in response but never gates the plan
        Optional<UserDayStatus> statusOpt = dayStatusRepo
                .findByIdUserIdAndIdDate(userId, today);

        // Check for IN_PROGRESS session today
        Instant startOfDay = Zones.fitnessDayStart(today);
        Optional<WorkoutSession> inProgress = sessionRepo
                .findFirstByUserIdAndStatusAndFinishedAtAfter(
                        userId, "IN_PROGRESS", startOfDay);
        if (inProgress.isPresent()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status",    "IN_PROGRESS");
            response.put("sessionId", inProgress.get().getId());
            return response;
        }

        // Weekly goal hit check
        if (completedThisWeek >= weeklyGoal) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status",           "GOAL_HIT");
            response.put("isGoalHit",         true);
            response.put("completedThisWeek", completedThisWeek);
            response.put("weeklyGoal",        weeklyGoal);
            response.put("message",           "Weekly goal hit! Rest or go for a bonus session.");
            return response;
        }

        // Get today's template day — structure only, NO exercises
        int nextDayNumber = completedThisWeek + 1;

        List<Map<String, Object>> allDays;
        try {
            allDays = mapper.readValue(plan.getDays(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Could not read week plan", e);
        }

        Map<String, Object> templateDay = allDays.stream()
                .filter(d -> !"rest".equalsIgnoreCase(
                        (String) d.get("dayType")))
                .filter(d -> Integer.valueOf(nextDayNumber)
                        .equals(d.get("dayNumber")))
                .findFirst()
                .orElse(allDays.stream()
                        .filter(d -> !"rest".equalsIgnoreCase(
                                (String) d.get("dayType")))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException(
                                "No training day found")));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("planId",           plan.getPlanId());
        response.put("weekNumber",        plan.getWeekNumber());
        response.put("isGoalHit",         false);
        response.put("completedThisWeek", completedThisWeek);
        response.put("weeklyGoal",        weeklyGoal);
        response.put("dayNumber",         nextDayNumber);
        response.put("dayLabel",          templateDay.get("dayLabel"));
        response.put("dayType",           templateDay.get("dayType"));
        response.put("muscleGroups",      templateDay.get("muscleGroups"));
        response.put("includesCore",      templateDay.get("includesCore"));
        response.put("guidanceText",      templateDay.get("guidanceText"));
        response.put("cardioType",        templateDay.get("cardioType"));
        response.put("cardioDurationMin", templateDay.get("cardioDurationMin"));
        response.put("estimatedMins",     templateDay.get("estimatedMins"));
        response.put("fitnessLevel",      user.getFitnessLevel());
        response.put("status",  statusOpt.map(UserDayStatus::getStatus).orElse("PENDING"));
        statusOpt.ifPresent(s -> response.put("message", statusMessage(s.getStatus())));
        return response;
    }

    public Map<String, Object> getWeekPlan(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        LocalDate monday = LocalDate.now(APP_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        UserPlan plan = planRepo.findByUserIdAndWeekStartDate(userId, monday)
                .orElseGet(() -> generatePlan(userId));

        int weeklyGoal = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;

        // Count completed sessions this week so frontend knows progress
        Instant weekStart = monday.atStartOfDay(APP_ZONE).toInstant();
        Instant now       = Instant.now();
        int completedThisWeek = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekStart, now);

        try {
            List<Map<String, Object>> days = mapper.readValue(
                    plan.getDays(), new TypeReference<>() {});

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("planId",            plan.getPlanId());
            response.put("weekNumber",         plan.getWeekNumber());
            response.put("weekStartDate",      plan.getWeekStartDate().toString());
            response.put("fitnessLevel",       user.getFitnessLevel());
            response.put("weeklyGoal",         weeklyGoal);
            response.put("completedThisWeek",  completedThisWeek);
            response.put("days",               days);
            return response;
        } catch (Exception e) {
            log.error("Failed to parse plan JSON: {}", e.getMessage());
            throw new RuntimeException("Could not read plan", e);
        }
    }

    public Map<String, Object> generateTodaysPlan(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        LocalDate today = Zones.fitnessDayNow();

        // Gate 1 — user set a status today
        Optional<UserDayStatus> statusOpt = dayStatusRepo
                .findByIdUserIdAndIdDate(userId, today);
        if (statusOpt.isPresent()) {
            return Map.of(
                    "status",  statusOpt.get().getStatus(),
                    "message", statusMessage(statusOpt.get().getStatus()));
        }

        // Gate 2 — IN_PROGRESS session exists today
        Instant startOfDay = Zones.fitnessDayStart(today);
        Optional<WorkoutSession> inProgress = sessionRepo
                .findFirstByUserIdAndStatusAndFinishedAtAfter(userId, "IN_PROGRESS", startOfDay);
        if (inProgress.isPresent()) {
            try {
                List<Map<String, Object>> exercises = inProgress.get().getExercises() != null
                        ? mapper.readValue(inProgress.get().getExercises(), new TypeReference<>() {})
                        : List.of();
                return Map.of(
                        "status",    "IN_PROGRESS",
                        "sessionId", inProgress.get().getId(),
                        "exercises", exercises);
            } catch (Exception e) {
                log.error("Failed to parse IN_PROGRESS session exercises: {}", e.getMessage());
            }
        }

        // Gate 3 — get today's template day (needed for both cached and fresh responses)
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        UserPlan weekPlan = planRepo.findByUserIdAndWeekStartDate(userId, monday)
                .orElseGet(() -> generatePlan(userId));

        Instant weekStart = monday.atStartOfDay(APP_ZONE).toInstant();
        Instant now       = Instant.now();
        int completedThisWeek = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekStart, now);
        int nextDayNumber = completedThisWeek + 1;

        List<Map<String, Object>> allDays;
        try {
            allDays = mapper.readValue(weekPlan.getDays(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Could not read week plan", e);
        }

        Map<String, Object> templateDay = allDays.stream()
                .filter(d -> !"rest".equalsIgnoreCase((String) d.get("dayType")))
                .filter(d -> Integer.valueOf(nextDayNumber).equals(d.get("dayNumber")))
                .findFirst()
                .orElse(allDays.stream()
                        .filter(d -> !"rest".equalsIgnoreCase((String) d.get("dayType")))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No training day found")));

        String dayType       = (String) templateDay.get("dayType");
        String dayLabel      = (String) templateDay.get("dayLabel");
        String level         = user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE";
        double bw            = user.getWeightKg() != null ? user.getWeightKg().doubleValue() : 70.0;
        int weekNumber       = weekPlan.getWeekNumber();

        @SuppressWarnings("unchecked")
        List<String> muscleGroups = (List<String>) templateDay.get("muscleGroups");
        boolean includesCore  = Boolean.TRUE.equals(templateDay.get("includesCore"));
        String guidanceText   = (String) templateDay.get("guidanceText");
        Integer estimatedMins = (Integer) templateDay.get("estimatedMins");

        // Gate 4 — already generated today (return cached with full fields)
        Optional<DailyPlanGenerated> existing = dailyPlanRepo
                .findByIdUserIdAndIdDate(userId, today);
        if (existing.isPresent()) {
            try {
                List<Map<String, Object>> exercises = mapper.readValue(
                        existing.get().getExercises(), new TypeReference<>() {});
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status",           "GENERATED");
                response.put("weekNumber",        weekPlan.getWeekNumber());
                response.put("dayType",           existing.get().getDayType());
                response.put("dayLabel",          dayLabel);
                response.put("muscleGroups",      muscleGroups);
                response.put("estimatedMins",     estimatedMins);
                response.put("fitnessLevel",      user.getFitnessLevel());
                response.put("exercises",         exercises);
                response.put("sessionNote",       existing.get().getSessionNote());
                response.put("dayCoachTip",       existing.get().getDayCoachTip());
                response.put("cardioSuggestion",  existing.get().getCardioSuggestion() != null
                        ? mapper.readValue(existing.get().getCardioSuggestion(),
                                new TypeReference<Map<String, Object>>() {})
                        : null);
                return response;
            } catch (Exception e) {
                log.error("Failed to parse existing daily plan: {}", e.getMessage());
            }
        }

        // Build context for AI
        Instant since4Days  = today.minusDays(4).atStartOfDay(APP_ZONE).toInstant();
        Instant since14Days = today.minusDays(14).atStartOfDay(APP_ZONE).toInstant();

        Map<String, Exercise> exMap = exerciseMap();
        List<WorkoutSession> recentSessions = sessionRepo
                .findByUserIdAndStatusAndFinishedAtBetween(userId, "COMPLETED", since14Days, now);
        List<HistoricalSet> recentLogs = planHistoryService
                .getRecentLoggedSets(userId, since14Days, exMap);

        List<SessionFeedback> recentFeedback = feedbackRepo
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().limit(3).collect(Collectors.toList());

        HistoryAnalysis analysis = analyseHistory(
                recentSessions, recentLogs, List.of(), bw, level, exMap);

        // Build daily context blocks
        String recoveryBlock = buildRecoveryBlock(recentLogs, exMap);
        String historyBlock  = buildDailyHistoryBlock(analysis, exMap);
        String feedbackBlock = buildDailyFeedbackBlock(recentFeedback);
        String recentExBlock = buildRecentExercisesBlock(recentLogs, since4Days);

        String aiContextBlock = (user.getAiContext() != null && !user.getAiContext().isBlank())
                ? "PERSONAL CONTEXT: " + user.getAiContext() : "";

        // Build fitness summary block — additive: absent row falls back to default text
        String fitnessSummaryBlock = buildFitnessSummaryBlock(userId, muscleGroups);

        List<String> canonicalMuscles = muscleGroups.stream()
                .map(MuscleGroupUtil::canonicalize)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        // Compute exercise count: each target muscle gets perMuscle exercises
        String levelKey   = level.toUpperCase();
        int muscleCount   = canonicalMuscles.size();
        int perMuscle     = perMuscleCount(dayType);
        int exerciseCount = muscleCount * perMuscle;

        String userPrompt = AiPrompts.DAILY_EXERCISE_USER
                .replace("{name}",                 PromptSanitiser.sanitise(
                        user.getDisplayName() != null ? user.getDisplayName() : "Athlete"))
                .replace("{gender}",               user.getGender() != null ? user.getGender() : "Not specified")
                .replace("{weightKg}",             user.getWeightKg() != null ? user.getWeightKg().toString() : "70")
                .replace("{heightCm}",             user.getHeightCm() != null ? user.getHeightCm().toString() : "Not specified")
                .replace("{fitnessLevel}",         level)
                .replace("{goal}",                 user.getGoal() != null ? user.getGoal() : "BUILD_MUSCLE")
                .replace("{weekNumber}",           String.valueOf(weekNumber))
                .replace("{healthConditions}",     formatHealthConditions(user.getHealthConditions()))
                .replace("{aiContext}",            aiContextBlock)
                .replace("{fitnessSummaryBlock}",  fitnessSummaryBlock)
                .replace("{dayLabel}",             dayLabel)
                .replace("{muscleGroups}",         String.join(", ", canonicalMuscles))
                .replace("{exerciseCount}",        String.valueOf(exerciseCount))
                .replace("{perMuscle}",            String.valueOf(perMuscle))
                .replace("{includesCore}",         String.valueOf(includesCore))
                .replace("{estimatedMins}",        String.valueOf(estimatedMins != null ? estimatedMins : 45))
                .replace("{guidanceText}",         guidanceText != null ? guidanceText : "")
                .replace("{recoveryBlock}",        recoveryBlock)
                .replace("{historyBlock}",         historyBlock + "\n" + recentExBlock)
                .replace("{feedbackBlock}",        feedbackBlock);

        // Call AI or fallback
        String exercises;
        String sessionNote      = null;
        String dayCoachTip      = null;
        String cardioSuggestion = null;

        if (openAiKey != null && !openAiKey.isBlank()) {
            log.info("[PROMPT_DEBUG_META] userId={} dayType={} muscleCount={} perMuscle={} exerciseCount={} muscleGroupsRaw={} muscleGroupsCanonical={}",
                    userId, dayType, muscleCount, perMuscle, exerciseCount, muscleGroups, canonicalMuscles);
            log.info("[PROMPT_DEBUG_BODY] userId={} fullPrompt=\n{}", userId, userPrompt);
            String aiResponse = callDailyOpenAi(userPrompt);
            if (aiResponse != null) {
                try {
                    String json = aiResponse.trim();
                    if (json.startsWith("```")) {
                        json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
                    }
                    Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});
                    exercises        = mapper.writeValueAsString(parsed.get("exercises"));
                    sessionNote      = (String) parsed.get("sessionNote");
                    dayCoachTip      = (String) parsed.get("dayCoachTip");
                    Object cardio    = parsed.get("cardioSuggestion");
                    cardioSuggestion = cardio != null ? mapper.writeValueAsString(cardio) : null;
                } catch (Exception e) {
                    log.warn("Failed to parse daily AI response: {}", e.getMessage());
                    exercises = buildFallbackExercisesJson(dayType, exMap, analysis, bw, level);
                }
            } else {
                exercises = buildFallbackExercisesJson(dayType, exMap, analysis, bw, level);
            }
        } else {
            exercises = buildFallbackExercisesJson(dayType, exMap, analysis, bw, level);
        }

        // Build response + enrich BEFORE saving to DB
        try {
            List<Map<String, Object>> exerciseList = mapper.readValue(
                    exercises, new TypeReference<>() {});

            // Build full plan exercise ID list to exclude from swap suggestions
            List<String> allExerciseIdsInPlan = exerciseList.stream()
                    .map(ex -> (String) ex.getOrDefault("exerciseId", ""))
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());

            // Enrich with swap alternatives
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (Map<String, Object> ex : exerciseList) {
                Map<String, Object> copy = new LinkedHashMap<>(ex);
                String exId = (String) copy.getOrDefault("exerciseId", "");
                Exercise entity = exMap.get(exId);
                String muscleGrp = entity != null
                        ? entity.getMuscleGroup() : (String) copy.get("muscleGroup");
                copy.put("equipment",    entity != null ? entity.getEquipment() : null);
                copy.put("isBodyweight", entity != null && entity.isBodyweight());
                copy.put("swapAlternatives", getSwapsFromDb(exId, muscleGrp, allExerciseIdsInPlan));
                enriched.add(copy);
            }

            // Store enriched plan (with swapAlternatives) in daily_plan_generated
            DailyPlanGenerated plan = new DailyPlanGenerated();
            plan.setId(new DailyPlanGeneratedId(userId, today));
            plan.setDayType(dayType);
            plan.setExercises(mapper.writeValueAsString(enriched));
            plan.setSessionNote(sessionNote);
            plan.setDayCoachTip(dayCoachTip);
            plan.setCardioSuggestion(cardioSuggestion);
            dailyPlanRepo.save(plan);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status",          "GENERATED");
            response.put("weekNumber",       weekNumber);
            response.put("dayType",          dayType);
            response.put("dayLabel",         dayLabel);
            response.put("muscleGroups",     muscleGroups);
            response.put("includesCore",     includesCore);
            response.put("estimatedMins",    estimatedMins);
            response.put("fitnessLevel",     user.getFitnessLevel());
            response.put("exercises",        enriched);
            response.put("sessionNote",      sessionNote);
            response.put("dayCoachTip",      dayCoachTip);
            response.put("cardioSuggestion", cardioSuggestion != null
                    ? mapper.readValue(cardioSuggestion, new TypeReference<Map<String, Object>>() {})
                    : null);
            return response;
        } catch (Exception e) {
            log.error("Failed to build daily plan response: {}", e.getMessage());
            throw new RuntimeException("Could not build today's plan", e);
        }
    }

    public Map<String, Object> setTodayStatus(UUID userId, String status, LocalDate targetDate) {
        LocalDate today = targetDate;

        // Block if IN_PROGRESS session exists
        Instant startOfDay = Zones.fitnessDayStart(today);
        boolean hasActiveSession = sessionRepo
                .findFirstByUserIdAndStatusAndFinishedAtAfter(userId, "IN_PROGRESS", startOfDay)
                .isPresent();
        if (hasActiveSession) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_IN_PROGRESS",
                    "Cannot set status — you have an active session in progress.");
        }

        String previousStatus = dayStatusRepo.findByIdUserIdAndIdDate(userId, today)
                .map(UserDayStatus::getStatus)
                .orElse(null);

        UserDayStatus dayStatus = new UserDayStatus(userId, today, status);
        dayStatusRepo.save(dayStatus);

        if (!status.equals(previousStatus)) {
            try {
                feedEventWriter.writeStatusChanged(userId, today, status, previousStatus);
            } catch (Exception e) {
                log.warn("Failed to write STATUS_CHANGED feed for user={}: {}", userId, e.getMessage());
            }
        }

        return Map.of(
                "status",  status,
                "message", statusMessage(status));
    }

    private String statusMessage(String status) {
        return switch (status) {
            case "REST"       -> "Recovery is part of the plan. Your muscles are growing right now. See you tomorrow 💪";
            case "TRAVELLING" -> "Staying consistent while travelling is hard. We'll pick up tomorrow — your progress is safe.";
            case "BUSY"       -> "Life happens. One missed day won't break your progress. Come back when you're ready.";
            case "SICK"       -> "Rest up and take care of yourself. Your health comes first — we'll be here when you're back 🙏";
            default           -> "Take your time. We'll be here when you're ready.";
        };
    }

    // ── History analysis ──────────────────────────────────────────────

    private HistoryAnalysis analyseHistory(List<WorkoutSession> sessions,
                                            List<HistoricalSet> logs,
                                            List<UserPlan> pastPlans,
                                            double bw, String level,
                                            Map<String, Exercise> exMap) {
        // Weight progression per exercise: exerciseId -> best recent weight
        Map<String, BigDecimal> recentBestWeight = new LinkedHashMap<>();
        Map<String, Integer>    sessionCountPerExercise = new LinkedHashMap<>();

        for (HistoricalSet hs : logs) {
            String id = hs.exerciseId();
            if (id == null || hs.weightKg() == null) continue;
            recentBestWeight.merge(id, hs.weightKg(), BigDecimal::max);
            sessionCountPerExercise.merge(id, 1, Integer::sum);
        }

        // Avoidance patterns: exercises_skipped across recent sessions (last 3)
        Map<String, Integer> skipCounts = new LinkedHashMap<>();
        List<WorkoutSession> last3 = sessions.stream()
                .sorted(Comparator.comparing(WorkoutSession::getFinishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3).collect(Collectors.toList());
        for (WorkoutSession s : last3) {
            String[] skipped = s.getExercisesSkipped();
            if (skipped != null) {
                for (String ex : skipped) skipCounts.merge(ex, 1, Integer::sum);
            }
        }
        Set<String> avoidanceExercises = skipCounts.entrySet().stream()
                .filter(e -> e.getValue() >= 2).map(Map.Entry::getKey).collect(Collectors.toSet());

        // Push / pull balance (last 4 weeks)
        long pushSessions = sessions.stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains("push")).count();
        long pullSessions = sessions.stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains("pull")).count();
        boolean pushPullImbalanced = pushSessions - pullSessions >= 2;

        // Muscle gap: which muscle groups had 0 sessions in the last 7 days
        Instant lastWeek = Instant.now().minus(7, ChronoUnit.DAYS);
        Set<String> trainedMuscles = new LinkedHashSet<>();
        for (HistoricalSet hs : logs) {
            if (hs.exerciseId() != null && !hs.muscleGroup().isEmpty()) {
                trainedMuscles.add(hs.muscleGroup());
            }
        }
        // We only check muscle gaps across recent logs in last 7 days
        List<UUID> recentSessionIds = sessions.stream()
                .filter(s -> s.getFinishedAt() != null && s.getFinishedAt().isAfter(lastWeek))
                .map(WorkoutSession::getId).collect(Collectors.toList());
        List<HistoricalSet> recentWeekLogs = logs.stream()
                .filter(hs -> recentSessionIds.contains(hs.sessionId()))
                .collect(Collectors.toList());
        Set<String> trainedLastWeek = recentWeekLogs.stream()
                .map(HistoricalSet::muscleGroup)
                .filter(m -> !m.isEmpty())
                .map(PlanService::broadGroupOf)
                .collect(Collectors.toSet());
        List<String> muscleGaps = STANDARD_MUSCLES.stream()
                .filter(m -> !trainedLastWeek.contains(m)).collect(Collectors.toList());

        // Progression stalls: exercise not progressed in 3+ sessions
        // (simple heuristic: if sessionCount >= 3 but no is_pr in last 3 sessions)
        Set<String> stalledExercises = new LinkedHashSet<>();
        Map<String, List<HistoricalSet>> byExId = logs.stream()
                .filter(hs -> hs.exerciseId() != null)
                .collect(Collectors.groupingBy(HistoricalSet::exerciseId));
        for (Map.Entry<String, List<HistoricalSet>> entry : byExId.entrySet()) {
            List<HistoricalSet> exLogs = entry.getValue();
            if (exLogs.size() >= 3) {
                boolean anyPr = exLogs.stream().anyMatch(HistoricalSet::isPr);
                if (!anyPr) stalledExercises.add(entry.getKey());
            }
        }

        // Adjusted weights: use recent best + 2.5kg if has history, else formula
        Map<String, Double> adjustedWeights = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : recentBestWeight.entrySet()) {
            String id = entry.getKey();
            if (avoidanceExercises.contains(id)) continue; // will be swapped out
            double next = entry.getValue()
                    .add(BigDecimal.valueOf(2.5))
                    .divide(BigDecimal.valueOf(2.5), 0, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(2.5))
                    .doubleValue();
            adjustedWeights.put(id, next);
        }

        return new HistoryAnalysis(
                recentBestWeight, adjustedWeights, avoidanceExercises,
                stalledExercises, muscleGaps, pushPullImbalanced,
                sessions.size(), skipCounts);
    }

    private record HistoryAnalysis(
            Map<String, BigDecimal> recentBestWeight,
            Map<String, Double>     adjustedWeights,
            Set<String>             avoidanceExercises,
            Set<String>             stalledExercises,
            List<String>            muscleGaps,
            boolean                 pushPullImbalanced,
            int                     totalSessionsInHistory,
            Map<String, Integer>    skipCounts
    ) {}

    // ── Rule-based plan builder ───────────────────────────────────────

    private List<Map<String, Object>> buildRuleBasedDays(int weeklyGoal,
                                                          Map<String, Exercise> exMap,
                                                          HistoryAnalysis analysis,
                                                          double bw, String level,
                                                          List<SplitTemplateDay> splitTemplate) {
        List<DayTemplate> templates = splitFor(weeklyGoal, level, splitTemplate, analysis);
        List<Map<String, Object>> days = new ArrayList<>();

        for (int i = 0; i < templates.size(); i++) {
            DayTemplate t = templates.get(i);
            int dayNum = i + 1;

            List<Map<String, Object>> exercises = t.exercises().stream().map(ed -> {
                Exercise ex = exMap.get(ed.id());

                // Swap if avoidance pattern detected
                String resolvedId = ed.id();
                if (analysis.avoidanceExercises().contains(ed.id()) && ex != null
                        && ex.getSwapAlternatives() != null && ex.getSwapAlternatives().length > 0) {
                    resolvedId = ed.id(); // keep but flag in why
                }

                boolean isBodyweight = "bodyweight".equals(ed.formula());
                Double kg = isBodyweight ? null
                        : analysis.adjustedWeights().containsKey(resolvedId)
                                ? analysis.adjustedWeights().get(resolvedId)
                                : calcWeight(ed.formula(), bw, level);

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("exerciseId",       resolvedId);
                m.put("exerciseName",     ex != null ? ex.getName() : resolvedId);
                m.put("sets",             ed.sets());
                m.put("reps",             ed.reps());
                m.put("restSeconds",      ed.restSeconds());
                m.put("suggestedKg",      kg);
                m.put("isBodyweight",     isBodyweight);
                m.put("equipment",        ex != null ? ex.getEquipment() : "BARBELL");
                m.put("muscleGroup",      ex != null ? ex.getMuscleGroup() : null);
                m.put("whyThisExercise",  buildWhyThisExercise(ed, analysis));
                m.put("coachTip",         ex != null ? ex.getCoachTip() : null);
                // Always store empty — dayResponse() re-fetches live swaps from DB on every GET
                m.put("swapAlternatives", new ArrayList<>());
                return m;
            }).collect(Collectors.toList());

            String rationale = buildRuleBasedRationale(t.dayType(), analysis, level);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("dayNumber",       dayNum);
            day.put("dayType",         t.dayType());
            day.put("sessionTitle",    t.title());
            day.put("durationMins",    t.durationMins());
            day.put("muscles",         t.muscles());
            day.put("exercises",       exercises);
            day.put("aiRationale",     rationale);
            day.put("sessionCoachTip", t.coachTip());
            days.add(day);
        }
        return days;
    }

    private String buildWhyThisExercise(ExerciseDef ed, HistoryAnalysis analysis) {
        String base = ed.why();
        if (analysis.stalledExercises().contains(ed.id())) {
            base += " Progression has stalled — focus on form quality this session before adding weight.";
        } else if (analysis.adjustedWeights().containsKey(ed.id())) {
            base += " Weight increased from last session based on your logged history.";
        }
        return base;
    }

    private String buildRuleBasedRationale(String dayType, HistoryAnalysis analysis, String level) {
        String base = switch (dayType) {
            case "Push A"    -> "Starting with the heaviest compound movement when fresh maximises strength development.";
            case "Push B"    -> "Incline emphasis shifts stress to upper chest, the area most commonly under-developed.";
            case "Pull"      -> "Row before pulldown builds back thickness first, then width.";
            case "Legs"      -> "Squatting first when energy is highest ensures best form on the most technical movement.";
            case "Full Body" -> "Compound movements ordered largest to smallest for peak central nervous system performance.";
            default          -> "Exercises sequenced to maximise compound strength first, then isolation volume.";
        };
        if (!analysis.muscleGaps().isEmpty() && analysis.muscleGaps().size() <= 3) {
            base += " This session prioritises " + String.join(" and ", analysis.muscleGaps())
                    + " which were undertrained last week.";
        }
        return base;
    }

    private String ruleBasedWeekRationale(int weeklyGoal, HistoryAnalysis analysis) {
        String split = switch (weeklyGoal) {
            case 2, 3 -> "Full Body split";
            case 5    -> "Push/Pull/Legs/Push/Pull split";
            case 6    -> "Push/Pull/Legs/Push/Pull/Full Body split";
            default   -> "Push/Pull/Legs/Push split";
        };
        String base = weeklyGoal + "-day " + split + " designed for "
                + (analysis.totalSessionsInHistory() == 0 ? "a new user — weights based on bodyweight formula."
                    : "your training history — weights updated from your recent sessions.");
        if (analysis.pushPullImbalanced()) base += " Extra pull work added to balance your push-heavy recent history.";
        if (!analysis.muscleGaps().isEmpty()) base += " " + String.join(", ", analysis.muscleGaps()) + " prioritised this week.";
        return base;
    }

    // ── AI plan call + parsing ────────────────────────────────────────

    private String callOpenAiPlan(User user, int weeklyGoal, HistoryAnalysis analysis,
                                   Map<String, Exercise> exMap,
                                   List<SplitTemplateDay> splitTemplate) {
        String historyBlock = buildHistoryBlock(analysis, exMap);
        String adjustmentLines = buildAdjustmentLines(analysis, exMap);
        String splitBlock = buildSplitBlock(splitTemplate);

        // Build feedback block from last 3 session ratings
        List<SessionFeedback> recentFeedback = feedbackRepo
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().limit(3).collect(Collectors.toList());

        String feedbackBlock = "";
        if (!recentFeedback.isEmpty()) {
            DateTimeFormatter fmt = DateTimeFormatter
                    .ofPattern("EEE dd MMM")
                    .withZone(ZoneId.of("Asia/Kolkata"));
            StringBuilder fb = new StringBuilder("\nRECENT SESSION FEEDBACK:\n");
            for (SessionFeedback f : recentFeedback) {
                String date = f.getCreatedAt() != null ? fmt.format(f.getCreatedAt()) : "recent";
                fb.append(date).append(": ").append(f.getRating());
                if (f.getNotes() != null && !f.getNotes().isBlank()) {
                    fb.append(" — ").append(f.getNotes());
                }
                fb.append("\n");
            }
            fb.append("""

Use this feedback to adjust NEXT WEEK's suggestedKg only.
Round all suggested weights to nearest real gym increment:
- Barbell exercises: nearest 2.5kg (e.g. 35, 37.5, 40)
- Dumbbell exercises: nearest 2kg (e.g. 8, 10, 12)
- Machine exercises: nearest 5kg (e.g. 30, 35, 40)
- Bodyweight exercises: adjust reps not weight

Weekly progression rules:
- TOO_EASY → increase suggestedKg by one increment
- GOOD (3 consecutive weeks) → increase by one increment
- HARD → keep same suggestedKg
- KILLED_ME → decrease by one increment
- Never increase more than one increment per week
- Never go below starting weight for this fitness level

When you change a weight from last week, explain why in that exercise's whyThisExercise field.
""");
            feedbackBlock = fb.toString();
        }

        String aiContextBlock = (user.getAiContext() != null && !user.getAiContext().isBlank())
                ? "PERSONAL CONTEXT FROM USER:\n" + user.getAiContext() + "\n" +
                  "(Use this to personalise the plan. If it mentions a timeframe like 'wedding in 3 months', " +
                  "adjust intensity and focus accordingly. If it mentions an injury not in health conditions, " +
                  "treat it as a health condition.)"
                : "";

        String userPrompt = AiPrompts.PLAN_GENERATION_USER
                .replace("{weeklyGoal}",      String.valueOf(weeklyGoal))
                .replace("{splitStructure}",  splitBlock)
                .replace("{name}",            PromptSanitiser.sanitise(user.getDisplayName() != null ? user.getDisplayName() : "Athlete"))
                .replace("{gender}",          user.getGender() != null ? user.getGender() : "Not specified")
                .replace("{weightKg}",        user.getWeightKg() != null ? user.getWeightKg().toString() : "70")
                .replace("{heightCm}",        user.getHeightCm() != null ? user.getHeightCm().toString() : "Not specified")
                .replace("{fitnessLevel}",    user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE")
                .replace("{goal}",            user.getGoal() != null ? user.getGoal() : "BUILD_MUSCLE")
                .replace("{healthConditions}", formatHealthConditions(user.getHealthConditions()))
                .replace("{aiContext}",        aiContextBlock)
                .replace("{historyBlock}",    historyBlock)
                .replace("{adjustmentLines}", adjustmentLines)
                .replace("{feedbackBlock}",   feedbackBlock);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o");
        body.put("max_tokens", 6000);
        body.put("temperature", 0.3);
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "You are an expert fitness coach and personal trainer. " +
                        "Generate structured workout plans in valid JSON format only. " +
                        "Be specific to the user's profile, fitness level, and goals. " +
                        "Return only the JSON object — no markdown, no explanation outside JSON."),
                Map.of("role", "user",   "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiKey);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    new HttpEntity<>(body, headers), Map.class);
            if (resp == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return msg != null ? (String) msg.get("content") : null;
        } catch (Exception e) {
            log.warn("OpenAI plan call failed: {}", e.getMessage());
            return null;
        }
    }

    private PlanParseResult parseAiPlan(String aiJson, Map<String, Exercise> exMap,
                                         HistoryAnalysis analysis, double bw, String level) {
        try {
            // Strip markdown code fences if present
            String json = aiJson.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
            }

            Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> aiDays = (List<Map<String, Object>>) parsed.get("days");
            String weekRationale  = (String) parsed.getOrDefault("weekRationale", "");
            String sessionCoachTip = (String) parsed.getOrDefault("sessionCoachTip",
                    "Focus on progressive overload this week.");

            // Enrich each exercise with swapAlternatives + ensure suggestedKg is sane
            if (aiDays != null) {
                int dayNum = 1;
                for (Map<String, Object> day : aiDays) {
                    day.put("dayNumber", dayNum++);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> exList =
                            (List<Map<String, Object>>) day.get("exercises");
                    if (exList != null) {
                        Set<String> usedIdsThisDay = new LinkedHashSet<>();
                        List<Map<String, Object>> deduped = new ArrayList<>();
                        for (Map<String, Object> ex : exList) {
                            String rawId = (String) ex.get("exerciseId");

                            // 1. Resolve alias → canonical DB id
                            String resolvedId = rawId != null
                                    ? EXERCISE_ID_ALIASES.getOrDefault(rawId, rawId)
                                    : null;
                            if (rawId != null && !rawId.equals(resolvedId)) {
                                ex.put("exerciseId", resolvedId);
                                log.debug("Alias resolved: {} → {}", rawId, resolvedId);
                            }

                            // 2. Skip duplicate exercises within the same day
                            if (resolvedId != null && !usedIdsThisDay.add(resolvedId)) {
                                log.debug("Duplicate exercise removed from day: {}", resolvedId);
                                continue;
                            }

                            // 3. Look up entity using resolved id
                            Exercise entity = resolvedId != null ? exMap.get(resolvedId) : null;
                            if (entity != null) {
                                ex.putIfAbsent("equipment",    entity.getEquipment());
                                ex.putIfAbsent("muscleGroup",  entity.getMuscleGroup());
                                ex.put("swapAlternatives",
                                        entity.getSwapAlternatives() != null
                                                ? entity.getSwapAlternatives() : new String[0]);
                                // Use adjusted weight if available (history-based overrides AI suggestion)
                                if (resolvedId != null && analysis.adjustedWeights().containsKey(resolvedId)) {
                                    ex.put("suggestedKg", analysis.adjustedWeights().get(resolvedId));
                                }
                            } else {
                                // 4. Unknown id — derive muscleGroup from EXERCISE_MUSCLE fallback map
                                if (ex.get("muscleGroup") == null && resolvedId != null) {
                                    String fallbackMuscle = resolveMuscleGroup(resolvedId, exMap);
                                    if (!fallbackMuscle.isEmpty()) {
                                        ex.put("muscleGroup", fallbackMuscle);
                                    }
                                }
                                log.warn("AI returned unknown exerciseId '{}' (raw: '{}') — keeping with no entity enrichment",
                                        resolvedId, rawId);
                            }

                            // 5. Coerce "Bodyweight"/"BW"/null strings → null Double + isBodyweight flag
                            Double parsedKg = parseSuggestedKg(ex.get("suggestedKg"));
                            ex.put("suggestedKg",  parsedKg);
                            ex.put("isBodyweight", parsedKg == null);

                            // 6. Always clear swapAlternatives before storing in JSONB —
                            //    dayResponse() re-fetches fresh object swaps from DB on every GET
                            ex.put("swapAlternatives", new ArrayList<>());
                            deduped.add(ex);
                        }
                        day.put("exercises", deduped);
                    }
                }
            }
            return new PlanParseResult(aiDays != null ? aiDays : List.of(), weekRationale, sessionCoachTip);
        } catch (Exception e) {
            log.warn("Failed to parse AI plan JSON, falling back to rule-based: {}", e.getMessage());
            return null;
        }
    }

    private record PlanParseResult(List<Map<String, Object>> days, String weekRationale, String sessionCoachTip) {}

    /**
     * Coerces the AI's suggestedKg value to a Double.
     * Returns null for bodyweight exercises (AI returns "Bodyweight", "BW", or empty string).
     */
    private Double parseSuggestedKg(Object value) {
        if (value == null) return null;
        String str = value.toString().trim();
        if (str.equalsIgnoreCase("Bodyweight") ||
                str.equalsIgnoreCase("BW") ||
                str.isEmpty()) return null;
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Insight saving ────────────────────────────────────────────────

    private void saveInsightsFromAnalysis(UUID userId, HistoryAnalysis analysis) {
        for (String exId : analysis.stalledExercises()) {
            AiInsight insight = new AiInsight();
            insight.setUserId(userId);
            insight.setInsightType("progression_stall");
            insight.setExerciseName(exId);
            insight.setFinding("No weight progression detected in last 3+ sessions for " + exId
                    + ". Plan will attempt +2.5kg next week.");
            insightRepo.save(insight);
        }

        for (String exId : analysis.avoidanceExercises()) {
            AiInsight insight = new AiInsight();
            insight.setUserId(userId);
            insight.setInsightType("avoidance_pattern");
            insight.setExerciseName(exId);
            insight.setFinding(exId + " skipped " + analysis.skipCounts().getOrDefault(exId, 0)
                    + " times in last 3 sessions. Consider a swap alternative.");
            insightRepo.save(insight);
        }

        for (String muscle : analysis.muscleGaps()) {
            AiInsight insight = new AiInsight();
            insight.setUserId(userId);
            insight.setInsightType("neglected_muscle");
            insight.setMuscleGroup(muscle);
            insight.setFinding(muscle + " had 0 training sessions in the last 7 days — prioritised in this week's plan.");
            insightRepo.save(insight);
        }

        if (analysis.pushPullImbalanced()) {
            AiInsight insight = new AiInsight();
            insight.setUserId(userId);
            insight.setInsightType("imbalance");
            insight.setFinding("Push sessions significantly exceed pull sessions in your recent history. " +
                    "Extra pull work added to this week's plan.");
            insightRepo.save(insight);
        }
    }

    // ── History block builders for prompts ────────────────────────────

    private String buildHistoryBlock(HistoryAnalysis analysis, Map<String, Exercise> exMap) {
        if (analysis.totalSessionsInHistory() == 0) return "No training history available — first plan for this user.";

        StringBuilder sb = new StringBuilder("RECENT TRAINING DATA:\n");

        // Weight progression
        if (!analysis.recentBestWeight().isEmpty()) {
            sb.append("Recent best weights:\n");
            for (Map.Entry<String, BigDecimal> e : analysis.recentBestWeight().entrySet()) {
                Exercise ex = exMap.get(e.getKey());
                String name = ex != null ? ex.getName() : e.getKey();
                boolean stalled = analysis.stalledExercises().contains(e.getKey());
                sb.append("- ").append(name).append(": ").append(e.getValue()).append("kg")
                  .append(stalled ? " (STALLING — no progression in 3+ sessions)" : "").append("\n");
            }
        }

        // Avoidance
        if (!analysis.avoidanceExercises().isEmpty()) {
            sb.append("Exercises avoided (2+ skips in last 3 sessions): ");
            sb.append(analysis.avoidanceExercises().stream()
                    .map(id -> { Exercise ex = exMap.get(id); return ex != null ? ex.getName() : id; })
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
        }

        // Muscle gaps
        if (!analysis.muscleGaps().isEmpty()) {
            sb.append("Muscle groups with 0 sessions last 7 days: ")
              .append(String.join(", ", analysis.muscleGaps())).append("\n");
        }

        // Push/pull
        if (analysis.pushPullImbalanced()) {
            sb.append("Push/pull balance: push sessions exceed pull by 2+ — add extra pull.\n");
        }

        return sb.toString().trim();
    }

    private String buildAdjustmentLines(HistoryAnalysis analysis, Map<String, Exercise> exMap) {
        if (analysis.totalSessionsInHistory() == 0) return "No history — use bodyweight-formula weights.";

        List<String> lines = new ArrayList<>();

        for (Map.Entry<String, Double> e : analysis.adjustedWeights().entrySet()) {
            Exercise ex = exMap.get(e.getKey());
            String name = ex != null ? ex.getName() : e.getKey();
            BigDecimal prev = analysis.recentBestWeight().get(e.getKey());
            if (prev != null) {
                lines.add("Increase " + name + " to " + e.getValue() + "kg (was " + prev + "kg)");
            }
        }

        for (String exId : analysis.avoidanceExercises()) {
            Exercise ex = exMap.get(exId);
            String name = ex != null ? ex.getName() : exId;
            String alt  = ex != null && ex.getSwapAlternatives() != null && ex.getSwapAlternatives().length > 0
                    ? ex.getSwapAlternatives()[0] : "an alternative";
            lines.add("Substitute " + name + " (avoidance pattern) with " + alt);
        }

        for (String muscle : analysis.muscleGaps()) {
            lines.add("Prioritise " + muscle + " — not trained in last 7 days");
        }

        if (analysis.pushPullImbalanced()) {
            lines.add("Add extra pull session or swap one push exercise for a pull movement");
        }

        return lines.isEmpty() ? "No specific adjustments — maintain current approach." : String.join("\n", lines);
    }

    private String buildSplitBlock(List<SplitTemplateDay> template) {
        if (template == null || template.isEmpty()) {
            return "Use Push/Pull/Legs split appropriate for the user's level and days.";
        }
        StringBuilder sb = new StringBuilder();
        for (SplitTemplateDay day : template) {
            if ("rest".equalsIgnoreCase(day.getDayType())) {
                sb.append("Day ").append(day.getDayNumber())
                  .append(": Active Rest — light walk only, no weight training\n");
                continue;
            }
            sb.append("Day ").append(day.getDayNumber())
              .append(": ").append(day.getDayLabel())
              .append(" — ").append(String.join(", ", day.getMuscleGroups()));
            if (day.isIncludesCore()) sb.append(" + Core finisher");
            if (day.getCardioType() != null) {
                sb.append(" | ").append(day.getCardioType().replace("_", " "))
                  .append(" ").append(day.getCardioDurationMin()).append(" min after");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildRecoveryBlock(List<HistoricalSet> logs, Map<String, Exercise> exMap) {
        if (logs.isEmpty()) return "RECOVERY STATUS: No recent sessions — all muscle groups fully recovered.";

        Map<String, Instant> lastTrainedPerMuscle = new LinkedHashMap<>();
        for (HistoricalSet hs : logs) {
            String muscle = hs.muscleGroup();
            if (muscle.isEmpty()) continue;
            if (hs.finishedAt() == null) continue;
            lastTrainedPerMuscle.merge(muscle, hs.finishedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }

        if (lastTrainedPerMuscle.isEmpty()) return "RECOVERY STATUS: No recent sessions — all muscle groups fully recovered.";

        StringBuilder sb = new StringBuilder("RECOVERY STATUS (hours since last trained):\n");
        Instant now = Instant.now();
        for (Map.Entry<String, Instant> entry : lastTrainedPerMuscle.entrySet()) {
            long hours = java.time.temporal.ChronoUnit.HOURS.between(entry.getValue(), now);
            String flag = hours < 48 ? "⚠️ UNDER 48HR — reduce volume or avoid"
                        : hours < 72 ? "✅ RECOVERING"
                        : "✅ FULLY RECOVERED";
            sb.append("- ").append(entry.getKey()).append(": ")
              .append(hours).append("hrs ago — ").append(flag).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildDailyHistoryBlock(HistoryAnalysis analysis, Map<String, Exercise> exMap) {
        if (analysis.totalSessionsInHistory() == 0)
            return "HISTORY: No training history — first session. Use conservative starting weights.";

        StringBuilder sb = new StringBuilder("LAST LOGGED WEIGHTS (use for progression):\n");
        for (Map.Entry<String, BigDecimal> e : analysis.recentBestWeight().entrySet()) {
            Exercise ex = exMap.get(e.getKey());
            String name = ex != null ? ex.getName() : e.getKey();
            boolean stalled = analysis.stalledExercises().contains(e.getKey());
            sb.append("- ").append(name).append(": ").append(e.getValue()).append("kg")
              .append(stalled ? " (STALLING — no progression in 3+ sessions)" : "").append("\n");
        }
        return sb.toString().trim();
    }

    private String buildRecentExercisesBlock(List<HistoricalSet> logs, Instant since) {
        List<String> recentExercises = logs.stream()
                .filter(hs -> hs.finishedAt() != null && hs.finishedAt().isAfter(since))
                .map(HistoricalSet::exerciseId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (recentExercises.isEmpty())
            return "RECENT EXERCISES (last 4 days): None — all exercises available.";

        return "RECENT EXERCISES (last 4 days — do NOT repeat these for same muscle group): "
                + String.join(", ", recentExercises);
    }

    private String buildDailyFeedbackBlock(List<SessionFeedback> feedback) {
        if (feedback.isEmpty()) return "";
        DateTimeFormatter fmt = DateTimeFormatter
                .ofPattern("EEE dd MMM").withZone(ZoneId.of("Asia/Kolkata"));
        StringBuilder sb = new StringBuilder("RECENT SESSION FEEDBACK (adjust weights accordingly):\n");
        for (SessionFeedback f : feedback) {
            String date = f.getCreatedAt() != null ? fmt.format(f.getCreatedAt()) : "recent";
            sb.append("- ").append(date).append(": ").append(f.getRating());
            if (f.getNotes() != null && !f.getNotes().isBlank()) {
                sb.append(" — ").append(f.getNotes());
            }
            sb.append("\n");
        }
        sb.append("Apply weight adjustments: TOO_EASY → +1 increment, GOOD × 3 → +1 increment, ")
          .append("HARD → same weight, KILLED_ME → -1 increment\n");
        return sb.toString().trim();
    }

    /**
     * Builds the fitness summary block for the AI prompt.
     *
     * <p>Additive: if no summary row exists yet (new user) the method returns a
     * fallback instruction rather than an empty block. This keeps the prompt valid
     * and the AI can still suggest sensible starting weights.
     *
     * <p>When a summary is present, {@code mainLiftStrength} entries are filtered
     * to the muscle groups targeted in today's session so the AI receives only
     * directly relevant strength references.
     */
    /** Package-private so {@code PlanServiceFitnessSummaryTest} can call it directly. */
    String buildFitnessSummaryBlock(UUID userId, List<String> muscleGroups) {
        Optional<FitnessSummary> summaryOpt = fitnessSummaryService.getSummary(userId);
        if (summaryOpt.isEmpty()) {
            return "(No historical data yet — this user is new. Use fitness level defaults for weight suggestions.)";
        }

        FitnessSummary summary = summaryOpt.get();

        // Canonicalize today's target muscles for filtering
        Set<String> canonicalTargets = muscleGroups == null ? Set.of() :
                muscleGroups.stream()
                        .map(MuscleGroupUtil::canonicalize)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();

        // Section 1: Strength reference — filtered to today's muscle groups
        List<FitnessSummary.MainLiftEntry> relevantLifts = summary.mainLiftStrength() == null
                ? List.of()
                : summary.mainLiftStrength().stream()
                        .filter(e -> e.maxKg() != null)
                        .filter(e -> canonicalTargets.isEmpty() ||
                                canonicalTargets.contains(MuscleGroupUtil.canonicalize(e.muscleGroup())))
                        .collect(Collectors.toList());

        if (!relevantLifts.isEmpty()) {
            LocalDate today = LocalDate.now(APP_ZONE);
            sb.append("Strength reference (use for weight suggestions):\n");
            for (FitnessSummary.MainLiftEntry lift : relevantLifts) {
                long daysAgo = -1;
                if (lift.lastLiftedDate() != null) {
                    try {
                        LocalDate liftDate = LocalDate.parse(lift.lastLiftedDate());
                        daysAgo = ChronoUnit.DAYS.between(liftDate, today);
                    } catch (Exception ignored) {}
                }
                sb.append("  - ").append(lift.exerciseId())
                  .append(": last max ").append(lift.maxKg()).append("kg")
                  .append(" × ").append(lift.maxKgReps());
                if (daysAgo >= 0) {
                    sb.append(", ").append(daysAgo)
                      .append(" day").append(daysAgo == 1 ? "" : "s").append(" ago");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Section 2: Weekly volume (last 2 weeks)
        if (summary.muscleGroupVolume() != null && !summary.muscleGroupVolume().isEmpty()) {
            Map<String, FitnessSummary.MuscleVolume> volumeMap = summary.muscleGroupVolume();
            Set<String> keysToShow = canonicalTargets.isEmpty() ? volumeMap.keySet() : canonicalTargets;
            List<String> parts = keysToShow.stream()
                    .filter(volumeMap::containsKey)
                    .map(muscle -> muscle + "=" + volumeMap.get(muscle).label())
                    .collect(Collectors.toList());
            if (!parts.isEmpty()) {
                sb.append("Weekly volume (last 2 weeks): ")
                  .append(String.join(", ", parts))
                  .append("\n\n");
            }
        }

        // Section 3: Last trained (days ago)
        if (summary.lastTrainedByMuscle() != null && !summary.lastTrainedByMuscle().isEmpty()) {
            Map<String, Integer> lastTrained = summary.lastTrainedByMuscle();
            Set<String> keysToShow = canonicalTargets.isEmpty() ? lastTrained.keySet() : canonicalTargets;
            List<String> parts = keysToShow.stream()
                    .filter(lastTrained::containsKey)
                    .map(muscle -> muscle + "=" + lastTrained.get(muscle))
                    .collect(Collectors.toList());
            if (!parts.isEmpty()) {
                sb.append("Last trained (days ago): ")
                  .append(String.join(", ", parts))
                  .append("\n\n");
            }
        }

        // Section 4: Recovery & progression
        sb.append("Recovery & progression:\n");
        if (summary.weeklyConsistency() != null) {
            FitnessSummary.WeeklyConsistency c = summary.weeklyConsistency();
            sb.append("  - Consistency: ").append(c.consistencyLabel())
              .append(" (").append(String.format("%.1f", c.avgSessionsPerWeek()))
              .append(" sessions/week vs goal of ").append(c.weeklyGoal()).append(")\n");
        }
        if (summary.rpeTrend() != null) {
            FitnessSummary.RpeTrend r = summary.rpeTrend();
            sb.append("  - Recent effort: ").append(r.trendLabel())
              .append(" (").append(r.previousWindowLabel())
              .append(" → ").append(r.currentWindowLabel()).append(")\n");
        }
        if (summary.prActivity() != null) {
            FitnessSummary.PrActivity p = summary.prActivity();
            sb.append("  - Progression: ").append(p.progressionLabel())
              .append(" (").append(p.prCountLast4Weeks()).append(" PR")
              .append(p.prCountLast4Weeks() == 1 ? "" : "s").append(" in last 4 weeks)\n");
        }

        return sb.toString().trim();
    }

    private String buildFallbackExercisesJson(String dayType, Map<String, Exercise> exMap,
                                               HistoryAnalysis analysis, double bw, String level) {
        try {
            List<Map<String, Object>> exercises = buildExercisesFromTemplate(
                    dayType, exMap, analysis, bw, level);
            return mapper.writeValueAsString(exercises);
        } catch (Exception e) {
            log.error("Failed to build fallback exercises: {}", e.getMessage());
            return "[]";
        }
    }

    private String callDailyOpenAi(String userPrompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 3000);
            body.put("temperature", 0.3);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", AiPrompts.DAILY_EXERCISE_SYSTEM),
                    Map.of("role", "user",   "content", userPrompt)));

            String jsonBody = mapper.writeValueAsString(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiKey);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions",
                    entity, Map.class);
            if (resp == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            @SuppressWarnings("unchecked")
            Map<String, Object> msg =
                    (Map<String, Object>) choices.get(0).get("message");
            return msg != null ? (String) msg.get("content") : null;

        } catch (Exception e) {
            log.warn("Daily OpenAI call failed: {}", e.getMessage());
            return null;
        }
    }

    private String formatHealthConditions(String[] conditions) {
        if (conditions == null || conditions.length == 0) return "None";
        return String.join(", ", conditions);
    }

    // ── Plan reading helpers ──────────────────────────────────────────

    private List<Map<String, Object>> getSwapsFromDb(String exerciseId, String muscleGroup,
                                                      List<String> allExerciseIdsInPlan) {
        if (muscleGroup == null || muscleGroup.isBlank()) {
            log.info("Swap lookup skipped — no muscleGroup for exerciseId={}", exerciseId);
            return Collections.emptyList();
        }
        log.info("Swap lookup: exerciseId={}, muscleGroup={}", exerciseId, muscleGroup);
        List<String> excludeIds = (allExerciseIdsInPlan != null && !allExerciseIdsInPlan.isEmpty())
                ? allExerciseIdsInPlan : List.of(exerciseId);
        List<Map<String, Object>> swaps = exerciseRepo
                .findByMuscleGroupIgnoreCaseAndIdNotIn(muscleGroup, excludeIds)
                .stream()
                .limit(3)
                .map(e -> {
                    Map<String, Object> swap = new LinkedHashMap<>();
                    swap.put("exerciseId",   e.getId());
                    swap.put("exerciseName", e.getName());
                    swap.put("equipment",    e.getEquipment());
                    swap.put("isBodyweight", e.isBodyweight());
                    return swap;
                })
                .collect(Collectors.toList());
        log.info("Swaps found: {} for exerciseId={}", swaps.size(), exerciseId);
        return swaps;
    }

    private Map<String, Object> dayResponse(UserPlan plan, int dayNumber, User user,
                                             int completedThisWeek, int weeklyGoal,
                                             Instant weekStart, Instant now) {
        try {
            List<Map<String, Object>> days = mapper.readValue(
                    plan.getDays(), new TypeReference<>() {});

            // Find the template day — skip rest days
            Map<String, Object> templateDay = days.stream()
                    .filter(d -> !"rest".equalsIgnoreCase((String) d.get("dayType")))
                    .filter(d -> Integer.valueOf(dayNumber).equals(d.get("dayNumber")))
                    .findFirst()
                    .orElse(days.stream()
                            .filter(d -> !"rest".equalsIgnoreCase((String) d.get("dayType")))
                            .skip(dayNumber - 1)
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No training day found")));

            // Gather recent history for AI context
            Instant since2Weeks = now.minus(14, java.time.temporal.ChronoUnit.DAYS);
            Map<String, Exercise> exMap = exerciseMap();
            List<WorkoutSession> recentSessions = sessionRepo
                    .findByUserIdAndStatusAndFinishedAtBetween(
                            user.getId(), "COMPLETED", since2Weeks, now);
            List<HistoricalSet> recentLogs = planHistoryService
                    .getRecentLoggedSets(user.getId(), since2Weeks, exMap);

            // Build history analysis for AI
            HistoryAnalysis analysis = analyseHistory(
                    recentSessions, recentLogs, List.of(), user.getWeightKg() != null
                            ? user.getWeightKg().doubleValue() : 70.0,
                    user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE", exMap);

            // Get muscle groups for today
            @SuppressWarnings("unchecked")
            List<String> muscleGroups = (List<String>) templateDay.get("muscleGroups");
            boolean includesCore = Boolean.TRUE.equals(templateDay.get("includesCore"));
            String dayLabel      = (String) templateDay.get("dayLabel");
            String dayType       = (String) templateDay.get("dayType");

            // Generate exercises via AI (or rule-based fallback)
            List<Map<String, Object>> exercises;
            if (openAiKey != null && !openAiKey.isBlank()) {
                String aiJson = callOpenAiPlan(user,
                        user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4,
                        analysis, exMap, List.of());
                if (aiJson != null) {
                    PlanParseResult parsed = parseAiPlan(aiJson, exMap, analysis,
                            user.getWeightKg() != null ? user.getWeightKg().doubleValue() : 70.0,
                            user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE");
                    if (parsed != null && !parsed.days().isEmpty()) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> aiExercises =
                                (List<Map<String, Object>>) parsed.days().get(0).get("exercises");
                        exercises = aiExercises != null ? aiExercises : List.of();
                    } else {
                        exercises = buildExercisesFromTemplate(dayType, exMap, analysis,
                                user.getWeightKg() != null ? user.getWeightKg().doubleValue() : 70.0,
                                user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE");
                    }
                } else {
                    exercises = buildExercisesFromTemplate(dayType, exMap, analysis,
                            user.getWeightKg() != null ? user.getWeightKg().doubleValue() : 70.0,
                            user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE");
                }
            } else {
                exercises = buildExercisesFromTemplate(dayType, exMap, analysis,
                        user.getWeightKg() != null ? user.getWeightKg().doubleValue() : 70.0,
                        user.getFitnessLevel() != null ? user.getFitnessLevel() : "INTERMEDIATE");
            }

            // Build full plan exercise ID list to exclude from swap suggestions
            List<String> allExerciseIdsInPlan = exercises.stream()
                    .map(ex -> (String) ex.getOrDefault("exerciseId", ""))
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());

            // Enrich exercises with live swap alternatives
            List<Map<String, Object>> enrichedExercises = new ArrayList<>();
            for (Map<String, Object> ex : exercises) {
                Map<String, Object> exCopy = new LinkedHashMap<>(ex);
                String exId = (String) exCopy.getOrDefault("exerciseId", "");
                Exercise entity = exMap.get(exId);
                String muscleGrp = entity != null
                        ? entity.getMuscleGroup()
                        : (String) exCopy.get("muscleGroup");
                exCopy.put("swapAlternatives", getSwapsFromDb(exId, muscleGrp, allExerciseIdsInPlan));
                enrichedExercises.add(exCopy);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("planId",           plan.getPlanId());
            response.put("weekNumber",        plan.getWeekNumber());
            response.put("isGoalHit",         false);
            response.put("completedThisWeek", completedThisWeek);
            response.put("weeklyGoal",        weeklyGoal);
            response.put("dayNumber",         dayNumber);
            response.put("dayLabel",          dayLabel);
            response.put("dayType",           dayType);
            response.put("muscleGroups",      muscleGroups);
            response.put("includesCore",      includesCore);
            response.put("guidanceText",      templateDay.get("guidanceText"));
            response.put("cardioType",        templateDay.get("cardioType"));
            response.put("cardioDurationMin", templateDay.get("cardioDurationMin"));
            response.put("estimatedMins",     templateDay.get("estimatedMins"));
            response.put("fitnessLevel",      user.getFitnessLevel());
            response.put("exercises",         enrichedExercises);
            return response;

        } catch (Exception e) {
            log.error("Failed to build day response: {}", e.getMessage());
            throw new RuntimeException("Could not build today's plan", e);
        }
    }

    private List<Map<String, Object>> buildExercisesFromTemplate(
            String dayType, Map<String, Exercise> exMap,
            HistoryAnalysis analysis, double bw, String level) {

        DayTemplate template = DAY_TYPE_TO_TEMPLATE.getOrDefault(dayType, FULL_BODY);
        return template.exercises().stream().map(ed -> {
            Exercise ex = exMap.get(ed.id());
            boolean isBodyweight = "bodyweight".equals(ed.formula());
            Double kg = isBodyweight ? null
                    : analysis.adjustedWeights().containsKey(ed.id())
                            ? analysis.adjustedWeights().get(ed.id())
                            : calcWeight(ed.formula(), bw, level);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exerciseId",      ed.id());
            m.put("exerciseName",    ex != null ? ex.getName() : ed.id());
            m.put("sets",            ed.sets());
            m.put("reps",            ed.reps());
            m.put("restSeconds",     ed.restSeconds());
            m.put("suggestedKg",     kg);
            m.put("isBodyweight",    isBodyweight);
            m.put("equipment",       ex != null ? ex.getEquipment() : null);
            m.put("muscleGroup",     ex != null ? ex.getMuscleGroup() : null);
            m.put("whyThisExercise", buildWhyThisExercise(ed, analysis));
            m.put("coachTip",        ex != null ? ex.getCoachTip() : null);
            m.put("swapAlternatives", new ArrayList<>());
            return m;
        }).collect(Collectors.toList());
    }

    // ── Weight calculation ────────────────────────────────────────────

    private double calcWeight(String formula, double bw, String level) {
        double factor = switch (formula) {
            case "bench"       -> 0.40;
            case "squat"       -> 0.50;
            case "shoulder"    -> 0.20;
            case "latpulldown" -> 0.45;
            case "isolation"   -> 0.10;
            default            -> 0.0;
        };
        if (factor == 0.0) return 0.0;
        double multiplier = switch (level != null ? level : "") {
            case "BEGINNER"  -> 0.70;
            case "ADVANCED"  -> 1.20;
            default          -> 1.00;
        };
        return BigDecimal.valueOf(bw * factor * multiplier)
                .divide(BigDecimal.valueOf(2.5), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(2.5))
                .doubleValue();
    }

    // ── Week number ───────────────────────────────────────────────────

    public int weekNumberFor(User user, LocalDate targetMonday) {
        LocalDate createdMonday = user.getCreatedAt()
                .atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) ChronoUnit.WEEKS.between(createdMonday, targetMonday) + 1;
    }

    private Map<String, Exercise> exerciseMap() {
        return exerciseRepo.findAll().stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));
    }

    // ── Static reference data ─────────────────────────────────────────

    private static final List<String> STANDARD_MUSCLES =
            List.of("Chest", "Back", "Shoulders", "Legs", "Arms", "Core");

    private static final Map<String, String> EXERCISE_MUSCLE = Map.ofEntries(
            Map.entry("bench-press",       "Chest"),
            Map.entry("incline-db-press",  "Chest"),
            Map.entry("cable-flyes",       "Chest"),
            Map.entry("push-ups",          "Chest"),
            Map.entry("dips",              "Chest"),
            Map.entry("barbell-row",       "Back"),
            Map.entry("lat-pulldown",      "Back"),
            Map.entry("pull-ups",          "Back"),
            Map.entry("shoulder-press",    "Shoulders"),
            Map.entry("lateral-raises",    "Shoulders"),
            Map.entry("overhead-press",    "Shoulders"),
            Map.entry("squat",             "Legs"),
            Map.entry("leg-press",         "Legs"),
            Map.entry("romanian-deadlift", "Legs"),
            Map.entry("leg-curl",          "Legs"),
            Map.entry("bicep-curl",        "Arms"),
            Map.entry("tricep-pushdowns",  "Arms"),
            Map.entry("plank",             "Core"),
            Map.entry("crunches",          "Core")
    );

    /**
     * Resolve muscle group for an exercise by querying the loaded exercise map first,
     * then falling back to the static EXERCISE_MUSCLE map. Returns empty string if not found.
     */
    private String resolveMuscleGroup(String exerciseId, Map<String, Exercise> exMap) {
        if (exerciseId == null) return "";
        if (exMap != null && exMap.containsKey(exerciseId)) {
            String dbMuscle = exMap.get(exerciseId).getMuscleGroup();
            if (dbMuscle != null && !dbMuscle.trim().isEmpty()) {
                String canonical = canonicalizeMuscle(dbMuscle);
                if (!canonical.isEmpty()) return canonical;
            }
        }
        return EXERCISE_MUSCLE.getOrDefault(exerciseId, "");
    }

    /**
     * Maps a canonical sub-muscle name back to its broad STANDARD_MUSCLES group.
     * Used only for muscle-gap detection — never for exercise counting.
     */
    private static String broadGroupOf(String canonical) {
        return switch (canonical) {
            case "Quads", "Hamstrings", "Glutes", "Calves" -> "Legs";
            case "Triceps", "Biceps"                         -> "Arms";
            case "Front Delts", "Rear Delts"                 -> "Shoulders";
            default -> canonical;
        };
    }

    /** Delegates to {@link MuscleGroupUtil#canonicalize(String)}. */
    private String canonicalizeMuscle(String dbValue) {
        String result = MuscleGroupUtil.canonicalize(dbValue);
        if (result.isEmpty() && dbValue != null && !dbValue.isBlank()) {
            log.debug("Unmapped DB muscleGroup value: '{}'", dbValue);
        }
        return result;
    }

    // ── Split templates ───────────────────────────────────────────────

    private List<DayTemplate> splitFor(int weeklyGoal, String level,
                                        List<SplitTemplateDay> splitTemplate,
                                        HistoryAnalysis analysis) {
        if (analysis.pushPullImbalanced() && weeklyGoal == 4) {
            return List.of(PUSH_A, PULL, LEGS, PULL);
        }
        if (!splitTemplate.isEmpty()) {
            return splitTemplate.stream()
                    .filter(td -> !"rest".equalsIgnoreCase(td.getDayType()))
                    .map(td -> DAY_TYPE_TO_TEMPLATE.getOrDefault(td.getDayType(), FULL_BODY))
                    .collect(Collectors.toList());
        }
        // Hardcoded fallback — should never be hit if DB is seeded
        return switch (weeklyGoal) {
            case 1  -> List.of(FULL_BODY);
            case 2  -> List.of(FULL_BODY, FULL_BODY);
            case 3  -> List.of(PUSH_A, PULL, LEGS);
            case 5  -> List.of(PUSH_A, PULL, LEGS, PUSH_B, PULL);
            case 6  -> List.of(PUSH_A, PULL, LEGS, PUSH_B, PULL, FULL_BODY);
            case 7  -> List.of(PUSH_A, PULL, LEGS, PUSH_B, PULL, LEGS, FULL_BODY);
            default -> List.of(PUSH_A, PULL, LEGS, PUSH_B);
        };
    }

    // ── Exercise definitions ──────────────────────────────────────────

    private record ExerciseDef(String id, String formula, int sets, int reps, int restSeconds, String why) {}
    private record DayTemplate(String dayType, String title, int durationMins,
                                List<String> muscles, List<ExerciseDef> exercises, String coachTip) {}

    private static final DayTemplate PUSH_A = new DayTemplate(
            "Push A", "Chest, Shoulders & Triceps", 45,
            List.of("Chest", "Front delts", "Triceps"),
            List.of(
                    new ExerciseDef("bench-press",      "bench",     3, 8,  90, "Primary chest builder. Compound movement that drives the most strength adaptation."),
                    new ExerciseDef("incline-db-press", "bench",     3, 10, 75, "Targets upper chest — typically the lagging portion. DBs allow greater range of motion."),
                    new ExerciseDef("shoulder-press",   "shoulder",  3, 10, 75, "Essential for overhead strength. Targets front and side delts for complete shoulder development."),
                    new ExerciseDef("lateral-raises",   "isolation", 3, 12, 60, "Isolation for side delts — the muscle that makes shoulders look wide from the front."),
                    new ExerciseDef("tricep-pushdowns", "isolation", 3, 12, 60, "High-rep isolation that pumps blood into the triceps after compound loading.")
            ),
            "Keep rest times strict. The burn you feel on isolation work means the compounds did their job."
    );

    private static final DayTemplate PUSH_B = new DayTemplate(
            "Push B", "Upper Chest, Shoulders & Triceps", 45,
            List.of("Upper chest", "Shoulders", "Triceps"),
            List.of(
                    new ExerciseDef("incline-db-press", "bench",      4, 8,  90, "Shifts focus to upper chest — the area most people under-develop."),
                    new ExerciseDef("overhead-press",   "shoulder",   3, 8,  90, "Standing overhead builds core stability. Best exercise for overall shoulder mass."),
                    new ExerciseDef("lateral-raises",   "isolation",  3, 15, 60, "Higher reps here for complete side delt detail after heavy pressing."),
                    new ExerciseDef("cable-flyes",      "isolation",  3, 12, 60, "Cable keeps tension at peak contraction — isolation that dumbbells cannot replicate."),
                    new ExerciseDef("dips",             "bodyweight", 3, 10, 60, "Best tricep mass builder. Forward lean shifts load to chest for balanced development.")
            ),
            "Same push pattern, different angles. Variety in angle drives new adaptation."
    );

    private static final DayTemplate PULL = new DayTemplate(
            "Pull", "Back & Biceps", 45,
            List.of("Back", "Biceps", "Rear delts"),
            List.of(
                    new ExerciseDef("barbell-row",    "latpulldown", 4, 8,  90, "Primary back thickness builder. Heaviest horizontal pull for maximum back development."),
                    new ExerciseDef("lat-pulldown",   "latpulldown", 3, 10, 75, "Builds back width. Cable provides consistent tension throughout the full range."),
                    new ExerciseDef("pull-ups",       "bodyweight",  3, 8,  90, "Best bodyweight back exercise. Also builds grip strength and core integration."),
                    new ExerciseDef("bicep-curl",     "isolation",   3, 12, 60, "Direct bicep stimulation after back work. Forearms and brachialis also benefit.")
            ),
            "Think about pulling with your elbows, not your hands. This single cue transforms back sessions."
    );

    private static final DayTemplate LEGS = new DayTemplate(
            "Legs", "Legs & Glutes", 50,
            List.of("Quads", "Hamstrings", "Glutes"),
            List.of(
                    new ExerciseDef("squat",             "squat",     4, 8,  120, "The king of leg exercises. Recruits quads, glutes, hamstrings, and core simultaneously."),
                    new ExerciseDef("leg-press",         "squat",     3, 12,  90, "Allows higher volume without spinal load. Completes quad exhaustion after squats."),
                    new ExerciseDef("romanian-deadlift", "squat",     3, 10,  90, "Best hamstring stretch under load. Essential for posterior chain balance."),
                    new ExerciseDef("leg-curl",          "isolation", 3, 12,  60, "Direct hamstring isolation — fills the gap that RDL misses at the knee joint.")
            ),
            "Legs are the hardest session mentally. Focus on form over weight for the first 2 weeks."
    );

    private static final DayTemplate FULL_BODY = new DayTemplate(
            "Full Body", "Full Body Strength", 50,
            List.of("Chest", "Back", "Shoulders", "Legs", "Arms", "Core"),
            List.of(
                    new ExerciseDef("squat",            "squat",      3, 10, 90, "Largest muscle groups first when energy is highest."),
                    new ExerciseDef("bench-press",      "bench",      3, 8,  90, "Primary upper body push. Chest and front delt compound."),
                    new ExerciseDef("barbell-row",      "latpulldown",3, 10, 75, "Balances the pressing with an equal pulling movement."),
                    new ExerciseDef("shoulder-press",   "shoulder",   3, 10, 75, "Overhead strength is foundational for all pressing movements."),
                    new ExerciseDef("tricep-pushdowns", "isolation",  2, 12, 60, "Finishes the push muscles after compound work."),
                    new ExerciseDef("bicep-curl",       "isolation",  2, 12, 60, "Finishes the pull muscles. Superset with triceps if short on time."),
                    new ExerciseDef("plank",            "bodyweight", 3, 30, 45, "Core stability finisher. Integrates everything trained above.")
            ),
            "Full body sessions are comprehensive. If pressed for time, prioritise the first 4 exercises."
    );

    private static final Map<String, DayTemplate> DAY_TYPE_TO_TEMPLATE = Map.ofEntries(
            Map.entry("push_a",    PUSH_A),
            Map.entry("push_b",    PUSH_B),
            Map.entry("pull",      PULL),
            Map.entry("pull_a",    PULL),
            Map.entry("pull_b",    PULL),
            Map.entry("legs",      LEGS),
            Map.entry("legs_a",    LEGS),
            Map.entry("legs_b",    LEGS),
            Map.entry("full_body", FULL_BODY),
            Map.entry("upper_a",   PUSH_A),
            Map.entry("upper_b",   PULL),
            Map.entry("lower",     LEGS),
            Map.entry("lower_a",   LEGS),
            Map.entry("lower_b",   LEGS)
    );
}
