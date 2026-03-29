package com.fittribe.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.util.PromptSanitiser;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int    MIN_SETS = 4;
    private static final String MOCK_INSIGHT =
            "Great session — keep tracking your sets and weights consistently for personalised AI insights.";

    @Value("${openai.api-key:}")
    private String openAiKey;

    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository         setLogRepo;
    private final UserRepository           userRepo;
    private final ObjectMapper             mapper;
    private final RestTemplate             restTemplate = new RestTemplate();

    public AiService(WorkoutSessionRepository sessionRepo,
                     SetLogRepository setLogRepo,
                     UserRepository userRepo,
                     ObjectMapper mapper) {
        this.sessionRepo = sessionRepo;
        this.setLogRepo  = setLogRepo;
        this.userRepo    = userRepo;
        this.mapper      = mapper;
    }

    @Async
    public void generateAndSaveInsight(UUID userId, UUID sessionId) {
        try {
            log.debug("=== AI INSIGHT DEBUG ===");
            log.debug("Session: {}", sessionId);

            WorkoutSession session = sessionRepo.findById(sessionId).orElse(null);
            if (session == null) {
                log.debug("RETURNING MOCK — reason: session not found in DB");
                return;
            }

            List<SetLog> logs = setLogRepo.findBySessionId(sessionId);
            log.debug("Set logs count: {}", logs.size());
            log.debug("Min sets required: {}", MIN_SETS);
            log.debug("Logs size >= MIN_SETS: {}", logs.size() >= MIN_SETS);
            log.debug("Total volume on session: {}", session.getTotalVolumeKg());
            log.debug("All null weight: {}", logs.stream().allMatch(l -> l.getWeightKg() == null));
            logs.forEach(sl -> log.debug("  SetLog id={} exerciseId={} weightKg={} reps={} isPr={}",
                    sl.getId(), sl.getExerciseId(), sl.getWeightKg(), sl.getReps(), sl.getIsPr()));

            // Gate is set COUNT only — never volume. Bodyweight sessions (null weightKg) are
            // valid and must reach OpenAI as long as enough sets were logged.
            if (logs.size() < MIN_SETS) {
                log.debug("RETURNING MOCK — reason: logs.size()={} < MIN_SETS={}", logs.size(), MIN_SETS);
                sessionRepo.updateAiInsight(sessionId, MOCK_INSIGHT);
                return;
            }

            User user = userRepo.findById(userId).orElse(null);
            log.debug("OpenAI key present: {}", openAiKey != null && !openAiKey.isBlank());

            String insight;
            if (openAiKey == null || openAiKey.isBlank()) {
                log.debug("RETURNING MOCK — reason: openAiKey is blank");
                insight = MOCK_INSIGHT;
            } else {
                log.debug("CALLING OPENAI — proceeding with real insight");
                insight = callOpenAi(buildPrompt(user, session, logs));
            }

            sessionRepo.updateAiInsight(sessionId, insight);
            log.info("AI insight saved for session {} ({} chars)", sessionId, insight != null ? insight.length() : 0);

        } catch (Exception e) {
            log.error("AI insight generation failed for session {}", sessionId, e);
        }
    }

    // ── Prompt builder ────────────────────────────────────────────────

    private String buildPrompt(User user, WorkoutSession session, List<SetLog> logs) {
        String base = AiPrompts.DAILY_INSIGHT_USER
                .replace("{name}",         str(user, "displayName"))
                .replace("{gender}",       str(user, "gender"))
                .replace("{weightKg}",     user != null && user.getWeightKg() != null
                                               ? user.getWeightKg().toString() : "unknown")
                .replace("{fitnessLevel}", str(user, "fitnessLevel"))
                .replace("{goal}",         str(user, "goal"))
                .replace("{healthConditions}", buildHealthConditionsBlock(user))
                .replace("{durationMins}", session.getDurationMins() != null
                                               ? session.getDurationMins().toString() : "?")
                .replace("{totalVolumeKg}", buildVolumeDisplay(session, logs))
                .replace("{exerciseLines}", buildExerciseLines(logs))
                .replace("{prs}",           buildPrs(logs))
                .replace("{comparisonLines}", buildComparisonLines(session, logs))
                .replace("{historyBlock}",  buildHistoryBlock(session));
        return base + buildUnderweightBlock(user) + buildAdvancedBlock(user);
    }

    private String buildUnderweightBlock(User user) {
        if (user == null || user.getWeightKg() == null) return "";
        double kg = user.getWeightKg().doubleValue();
        boolean dangerouslyLow = ("male".equalsIgnoreCase(user.getGender())   && kg < 45)
                              || ("female".equalsIgnoreCase(user.getGender()) && kg < 40);
        if (!dangerouslyLow) return "";
        return "\nCRITICAL: This user weighs only " + user.getWeightKg() + "kg, which is dangerously low. " +
               "Your response MUST mention that they should consult a doctor and prioritise nutrition and " +
               "medical assessment before increasing training intensity. Frame this with care, not alarm.\n";
    }

    private String buildAdvancedBlock(User user) {
        if (user == null || !"ADVANCED".equalsIgnoreCase(user.getFitnessLevel())) return "";
        return "\nIMPORTANT: This is an ADVANCED lifter. Do NOT give beginner form cues. " +
               "Give advanced advice: periodisation, progressive overload schemes, CNS fatigue " +
               "management, deload strategy, and competition prep considerations if relevant.\n";
    }

    private String buildHealthConditionsBlock(User user) {
        if (user == null) return "";
        String[] conditions = user.getHealthConditions();
        if (conditions == null || conditions.length == 0) return "";
        List<String> active = Arrays.stream(conditions)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toList());
        if (active.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("HEALTH CONDITIONS (MUST address in response): ")
          .append(String.join(", ", active)).append("\n");

        for (String condition : active) {
            switch (condition.toLowerCase()) {
                case "pcos" ->
                    sb.append("- User has PCOS: explicitly mention that strength training helps regulate " +
                              "hormones and insulin resistance. Fat loss is harder with PCOS — acknowledge " +
                              "this and emphasise consistency over results.\n");
                case "pregnancy" ->
                    sb.append("- User is PREGNANT: prioritise safety above all. Mention pelvic floor " +
                              "engagement, avoid advising supine positions after first trimester, never " +
                              "suggest increasing intensity or weight. Remind to consult OB-GYN.\n");
                case "postpartum" ->
                    sb.append("- User is POSTPARTUM: focus on gentle core reconnection, pelvic floor " +
                              "recovery. Do not push for weight loss speed. Acknowledge body has been through " +
                              "major change and patience is key.\n");
                case "diabetes" ->
                    sb.append("- User has DIABETES: mention monitoring blood sugar before and after exercise. " +
                              "Avoid advice that pushes extreme exertion. Consistent moderate exercise is " +
                              "beneficial for blood sugar regulation.\n");
                case "hypertension" ->
                    sb.append("- User has HYPERTENSION: explicitly warn against breath-holding (Valsalva " +
                              "maneuver) during lifts. Recommend exhaling on exertion. Avoid heavy maximal " +
                              "lifts. Consistent moderate exercise helps BP.\n");
                case "heart condition" ->
                    sb.append("- User has a HEART CONDITION: strongly recommend they consult their " +
                              "cardiologist before increasing intensity. Keep advice conservative and " +
                              "low-intensity. Never suggest pushing through discomfort.\n");
                case "joint issues" ->
                    sb.append("- User has JOINT ISSUES: commend low-impact exercise choices. Warn against " +
                              "high-impact movements. Suggest controlled range of motion, not locking joints.\n");
                case "back pain" ->
                    sb.append("- User has BACK PAIN: avoid recommending spinal flexion under load. Core " +
                              "stability work is beneficial. Mention neutral spine importance during all exercises.\n");
                case "knee replacement" ->
                    sb.append("- User has had KNEE REPLACEMENT: avoid any recommendation of squats, lunges " +
                              "or loaded knee flexion. Machine-based isolated movements are appropriate. " +
                              "Suggest consulting physiotherapist for progression.\n");
                case "hernia" ->
                    sb.append("- User has HERNIA: warn against any exercise that increases intra-abdominal " +
                              "pressure — no heavy squats, deadlifts, or straining. Consult surgeon before " +
                              "progressing any abdominal exercises.\n");
                case "asthma" ->
                    sb.append("- User has ASTHMA: mention breathing technique during lifts — exhale on " +
                              "exertion. Ensure inhaler is accessible. Strength training is generally safer " +
                              "than cardio for asthma — acknowledge this choice positively.\n");
                case "thyroid issues" ->
                    sb.append("- User has THYROID ISSUES: acknowledge that metabolism may be affected, making " +
                              "fat loss or weight gain slower. Encourage consistency and do not suggest the " +
                              "user is not working hard enough.\n");
                case "scoliosis" ->
                    sb.append("- User has SCOLIOSIS: core strengthening is beneficial. Avoid heavy loaded " +
                              "spinal flexion or rotation. Symmetrical exercises preferred. Mention " +
                              "physiotherapist guidance is valuable.\n");
                case "endometriosis" ->
                    sb.append("- User has ENDOMETRIOSIS: acknowledge pain flares are unpredictable. Never " +
                              "suggest pushing through pain. Rest days are as important as training days. " +
                              "Gentle consistency is better than intensity.\n");
                default ->
                    sb.append("- User has ").append(condition)
                      .append(": adjust advice to be conservative and safety-first.\n");
            }
        }

        sb.append("Your response MUST reference at least one of these conditions by name. " +
                  "Do not give generic advice.\n\n");
        return sb.toString();
    }

    private String buildExerciseLines(List<SetLog> logs) {
        Map<String, List<SetLog>> byExercise = groupByExercise(logs);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<SetLog>> entry : byExercise.entrySet()) {
            List<SetLog> sets = entry.getValue();
            int totalReps = sets.stream().mapToInt(sl -> sl.getReps() != null ? sl.getReps() : 0).sum();
            boolean isBodyweight = sets.stream().allMatch(sl -> sl.getWeightKg() == null);
            String weightPart;
            if (isBodyweight) {
                weightPart = "bodyweight";
            } else {
                long weightedCount = sets.stream().filter(sl -> sl.getWeightKg() != null).count();
                BigDecimal weightSum = sets.stream()
                        .map(SetLog::getWeightKg).filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal avgWeight = weightSum.divide(
                        BigDecimal.valueOf(weightedCount), 1, RoundingMode.HALF_UP);
                weightPart = avgWeight + "kg";
            }
            sb.append("- ").append(PromptSanitiser.sanitise(entry.getKey())).append(": ")
              .append(weightPart).append(" × ").append(totalReps)
              .append(" reps × ").append(sets.size()).append(" sets\n");
        }
        return sb.toString().trim();
    }

    private String buildVolumeDisplay(WorkoutSession session, List<SetLog> logs) {
        BigDecimal vol = session.getTotalVolumeKg();
        if (vol != null && vol.compareTo(BigDecimal.ZERO) > 0) return vol.toString() + "kg";
        boolean anyWeighted = logs.stream()
                .anyMatch(sl -> sl.getWeightKg() != null && sl.getWeightKg().compareTo(BigDecimal.ZERO) > 0);
        return anyWeighted ? (vol != null ? vol.toString() + "kg" : "?") : "bodyweight session";
    }

    private String buildPrs(List<SetLog> logs) {
        List<String> prs = logs.stream()
                .filter(sl -> Boolean.TRUE.equals(sl.getIsPr()))
                .map(sl -> PromptSanitiser.sanitise(
                        sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId()))
                .distinct()
                .collect(Collectors.toList());
        return prs.isEmpty() ? "none" : String.join(", ", prs);
    }

    private String buildComparisonLines(WorkoutSession session, List<SetLog> logs) {
        String plannedJson = session.getAiPlannedWeights();
        if (plannedJson == null || plannedJson.isBlank()) {
            return "No AI plan for this session — custom workout.";
        }
        try {
            List<Map<String, Object>> planned = mapper.readValue(
                    plannedJson, new TypeReference<>() {});

            // exerciseId -> plannedKg
            Map<String, Double> planMap = new LinkedHashMap<>();
            for (Map<String, Object> p : planned) {
                String id = (String) p.get("exerciseId");
                Object kg  = p.get("suggestedKg");
                if (id != null && kg instanceof Number) {
                    planMap.put(id, ((Number) kg).doubleValue());
                }
            }

            // exerciseId -> actual avg weight logged
            Map<String, List<SetLog>> byExercise = groupByExercise(logs);
            Map<String, Double> actualMap = new LinkedHashMap<>();
            for (Map.Entry<String, List<SetLog>> entry : byExercise.entrySet()) {
                // key here is exerciseName — need to re-key by exerciseId
            }
            Map<String, Double> actualByIdMap = new LinkedHashMap<>();
            for (SetLog sl : logs) {
                actualByIdMap.merge(sl.getExerciseId(),
                        sl.getWeightKg() != null ? sl.getWeightKg().doubleValue() : 0.0,
                        Double::max);
            }

            // Exercises in actualByIdMap — map exerciseId -> display name
            Map<String, String> nameById = logs.stream()
                    .filter(sl -> sl.getExerciseName() != null)
                    .collect(Collectors.toMap(
                            SetLog::getExerciseId, SetLog::getExerciseName,
                            (a, b) -> a, LinkedHashMap::new));

            Set<String> allIds = new LinkedHashSet<>();
            allIds.addAll(planMap.keySet());
            allIds.addAll(actualByIdMap.keySet());

            StringBuilder sb = new StringBuilder();
            for (String id : allIds) {
                String name = PromptSanitiser.sanitise(nameById.getOrDefault(id, id));
                Double planned2 = planMap.get(id);
                Double actual   = actualByIdMap.get(id);

                if (planned2 != null && actual != null) {
                    double diff = actual - planned2;
                    String diffStr = String.format("%+.1f", diff) + "kg " +
                            (diff >= 0 ? "above" : "below") + " plan";
                    sb.append(name).append(" — planned ").append(planned2).append("kg, logged ")
                      .append(actual).append("kg (").append(diffStr).append(")\n");
                } else if (actual != null) {
                    sb.append(name).append(" — added by user, ").append(actual).append("kg\n");
                } else if (planned2 != null) {
                    sb.append(name).append(" — planned ").append(planned2).append("kg, skipped\n");
                }
            }
            return sb.toString().trim().isEmpty()
                    ? "No AI plan for this session — custom workout."
                    : sb.toString().trim();
        } catch (Exception e) {
            log.warn("Could not parse aiPlannedWeights: {}", e.getMessage());
            return "No AI plan for this session — custom workout.";
        }
    }

    private String buildHistoryBlock(WorkoutSession session) {
        List<WorkoutSession> pastSessions = sessionRepo
                .findTop3ByUserIdAndStatusOrderByStartedAtDesc(session.getUserId(), "COMPLETED");
        pastSessions = pastSessions.stream()
                .filter(s -> !s.getId().equals(session.getId()))
                .limit(3)
                .collect(Collectors.toList());

        if (pastSessions.size() >= 3) {
            StringBuilder sb = new StringBuilder("LAST 3 SESSIONS VOLUME:\n");
            for (WorkoutSession past : pastSessions) {
                String date = past.getStartedAt() != null
                        ? LocalDate.ofInstant(past.getStartedAt(), ZoneOffset.UTC).toString()
                        : "unknown";
                sb.append(date).append(": ").append(past.getTotalVolumeKg()).append("kg\n");
            }
            sb.append("INSTRUCTION: Reference actual numbers. Identify trends or plateaus. Give one specific actionable tip.");
            return sb.toString();
        } else {
            return "INSTRUCTION: Check if weights suit body weight and level. Give form or safety tip. Encourage.";
        }
    }

    // ── OpenAI call ───────────────────────────────────────────────────

    private String callOpenAi(String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("max_tokens", 150);
        body.put("messages", List.of(
                Map.of("role", "system", "content", AiPrompts.DAILY_INSIGHT_SYSTEM),
                Map.of("role", "user",   "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiKey);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                "https://api.openai.com/v1/chat/completions",
                new HttpEntity<>(body, headers), Map.class);

        if (response == null) return MOCK_INSIGHT;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return MOCK_INSIGHT;

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return message != null ? (String) message.get("content") : MOCK_INSIGHT;
    }

    // ── Utilities ─────────────────────────────────────────────────────

    private Map<String, List<SetLog>> groupByExercise(List<SetLog> logs) {
        return logs.stream().collect(Collectors.groupingBy(
                sl -> sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId(),
                LinkedHashMap::new, Collectors.toList()));
    }

    private String str(User user, String field) {
        if (user == null) return "unknown";
        return switch (field) {
            case "displayName"  -> PromptSanitiser.sanitise(user.getDisplayName());
            case "gender"       -> user.getGender()       != null ? user.getGender()       : "unknown";
            case "fitnessLevel" -> user.getFitnessLevel() != null ? user.getFitnessLevel() : "unknown";
            case "goal"         -> user.getGoal()         != null ? user.getGoal()         : "unknown";
            default -> "unknown";
        };
    }
}
