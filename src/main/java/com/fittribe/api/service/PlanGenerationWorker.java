package com.fittribe.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.entity.DailyPlanGenerated;
import com.fittribe.api.entity.DailyPlanGeneratedId;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.DailyPlanGeneratedRepository;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles async plan generation via OpenAI.
 *
 * Separated from PlanService to enable Spring's @Async proxy interception.
 * Receives pre-computed userPrompt from PlanService (synchronously) and runs
 * the expensive OpenAI call asynchronously on a separate thread.
 */
@Service
public class PlanGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(PlanGenerationWorker.class);

    private final UserRepository userRepo;
    private final DailyPlanGeneratedRepository dailyPlanRepo;
    private final ExerciseRepository exerciseRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;
    private final PlanService planService;  // For helper methods

    @Value("${openai.api-key:}")
    private String openAiKey;

    public PlanGenerationWorker(
            UserRepository userRepo,
            DailyPlanGeneratedRepository dailyPlanRepo,
            ExerciseRepository exerciseRepo,
            RestTemplate restTemplate,
            ObjectMapper mapper,
            PlanService planService) {
        this.userRepo = userRepo;
        this.dailyPlanRepo = dailyPlanRepo;
        this.exerciseRepo = exerciseRepo;
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.planService = planService;
    }

    /**
     * Generate today's plan asynchronously via OpenAI.
     *
     * Called from PlanService after:
     * - All gates passed
     * - PENDING row created
     * - userPrompt already computed (expensive context work done synchronously)
     *
     * This method only needs to:
     * 1. Call OpenAI (blocking, but on async thread)
     * 2. Parse response
     * 3. Enrich with swap alternatives
     * 4. Save to DB with status="READY"
     * 5. On error, mark status="FAILED"
     */
    @Async
    public void generateTodaysPlanAsync(UUID userId, LocalDate today, String dayType,
                                        String dayLabel, Integer weekNumber,
                                        List<String> muscleGroups, Integer estimatedMins,
                                        String level, double bw, String userPrompt) {
        log.info("PlanGenerationWorker: started async generation for userId={}", userId);
        try {
            // Reload user for this async context
            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            // Call OpenAI (blocking, but on separate thread)
            String exercises;
            String sessionNote = null;
            String dayCoachTip = null;
            String cardioSuggestion = null;

            if (openAiKey != null && !openAiKey.isBlank()) {
                log.info("[ASYNC_OPENAI_CALL] userId={} calling OpenAI", userId);
                String aiResponse = callDailyOpenAi(userPrompt);
                if (aiResponse != null) {
                    try {
                        String json = aiResponse.trim();
                        if (json.startsWith("```")) {
                            json = json.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();
                        }
                        Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});
                        exercises = mapper.writeValueAsString(parsed.get("exercises"));
                        sessionNote = (String) parsed.get("sessionNote");
                        dayCoachTip = (String) parsed.get("dayCoachTip");
                        Object cardio = parsed.get("cardioSuggestion");
                        cardioSuggestion = cardio != null ? mapper.writeValueAsString(cardio) : null;
                        log.info("[ASYNC_OPENAI_SUCCESS] userId={} parsed response successfully", userId);
                    } catch (Exception e) {
                        log.warn("PlanGenerationWorker: Failed to parse daily AI response for userId={}: {}", userId, e.getMessage());
                        exercises = planService.buildFallbackExercisesJson(dayType, planService.exerciseMap(), null, bw, level);
                    }
                } else {
                    log.warn("PlanGenerationWorker: OpenAI returned null for userId={}", userId);
                    exercises = planService.buildFallbackExercisesJson(dayType, planService.exerciseMap(), null, bw, level);
                }
            } else {
                log.debug("PlanGenerationWorker: No OpenAI key, using fallback for userId={}", userId);
                exercises = planService.buildFallbackExercisesJson(dayType, planService.exerciseMap(), null, bw, level);
            }

            // Parse and enrich exercises with swap alternatives
            List<Map<String, Object>> exerciseList = mapper.readValue(exercises, new TypeReference<>() {});
            List<String> allExerciseIdsInPlan = exerciseList.stream()
                    .map(ex -> (String) ex.getOrDefault("exerciseId", ""))
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());

            Map<String, Exercise> exMap = planService.exerciseMap();
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (Map<String, Object> ex : exerciseList) {
                Map<String, Object> copy = new LinkedHashMap<>(ex);
                String exId = (String) copy.getOrDefault("exerciseId", "");
                Exercise entity = exMap.get(exId);
                String muscleGrp = entity != null
                        ? entity.getMuscleGroup() : (String) copy.get("muscleGroup");
                copy.put("equipment", entity != null ? entity.getEquipment() : null);
                copy.put("isBodyweight", entity != null && entity.isBodyweight());
                copy.put("swapAlternatives", planService.getSwapsFromDb(exId, muscleGrp, allExerciseIdsInPlan));
                enriched.add(copy);
            }

            // Fetch existing PENDING row and update with generated content
            Optional<DailyPlanGenerated> existingOpt = dailyPlanRepo
                    .findByIdUserIdAndIdDate(userId, today);
            if (existingOpt.isEmpty()) {
                log.error("PlanGenerationWorker: DailyPlanGenerated row missing for userId={} today={}", userId, today);
                return;
            }

            DailyPlanGenerated plan = existingOpt.get();
            plan.setExercises(mapper.writeValueAsString(enriched));
            plan.setSessionNote(sessionNote);
            plan.setDayCoachTip(dayCoachTip);
            plan.setCardioSuggestion(cardioSuggestion);
            plan.setStatus("READY");
            dailyPlanRepo.save(plan);

            log.info("PlanGenerationWorker: completed successfully for userId={}", userId);

        } catch (Exception e) {
            log.error("PlanGenerationWorker: FAILED for userId={}: {}", userId, e.getMessage(), e);
            try {
                Optional<DailyPlanGenerated> existingOpt = dailyPlanRepo
                        .findByIdUserIdAndIdDate(userId, today);
                if (existingOpt.isPresent()) {
                    DailyPlanGenerated plan = existingOpt.get();
                    plan.setStatus("FAILED");
                    dailyPlanRepo.save(plan);
                    log.info("PlanGenerationWorker: marked plan as FAILED for userId={}", userId);
                }
            } catch (Exception e2) {
                log.error("PlanGenerationWorker: could not mark plan as FAILED for userId={}: {}", userId, e2.getMessage());
            }
        }
    }

    /**
     * Call OpenAI API for daily exercise plan generation.
     */
    private String callDailyOpenAi(String userPrompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "gpt-4o");
            body.put("max_tokens", 3000);
            body.put("temperature", 0.3);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", AiPrompts.DAILY_EXERCISE_SYSTEM),
                    Map.of("role", "user", "content", userPrompt)));

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
            log.warn("PlanGenerationWorker: OpenAI call failed: {}", e.getMessage());
            return null;
        }
    }
}
