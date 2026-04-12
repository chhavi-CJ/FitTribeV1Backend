package com.fittribe.api.bonus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.RecoveryGateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BonusSessionServiceTest {

    private UserRepository            userRepo;
    private BonusSessionRepository    bonusRepo;
    private ExerciseRepository        exerciseRepo;
    private WorkoutSessionRepository  sessionRepo;
    private RecoveryGateService       recoveryGate;
    private SessionArchetypeResolver  resolver;
    private BonusSessionPromptBuilder promptBuilder;
    private ObjectMapper              mapper;
    private RestTemplate              restTemplate;
    private BonusSessionService       service;
    private UUID                      userId;
    private User                      user;

    @BeforeEach
    void setUp() {
        userRepo      = mock(UserRepository.class);
        bonusRepo     = mock(BonusSessionRepository.class);
        exerciseRepo  = mock(ExerciseRepository.class);
        sessionRepo   = mock(WorkoutSessionRepository.class);
        recoveryGate  = mock(RecoveryGateService.class);
        resolver      = mock(SessionArchetypeResolver.class);
        promptBuilder = mock(BonusSessionPromptBuilder.class);
        mapper        = new ObjectMapper();
        restTemplate  = mock(RestTemplate.class);

        service = new BonusSessionService(userRepo, bonusRepo, exerciseRepo, sessionRepo,
                recoveryGate, resolver, promptBuilder, mapper, restTemplate, "test-key");

        userId = UUID.randomUUID();
        user = buildUser();

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(sessionRepo.countByUserIdAndStatusAndSourceNotAndFinishedAtBetween(
                eq(userId), eq("COMPLETED"), eq("BONUS"), any(), any())).thenReturn(4);
        when(recoveryGate.computeRecoveryState(eq(userId), any())).thenReturn(Map.of());
        when(bonusRepo.countByIdUserIdAndIdDateBetween(eq(userId), any(), any())).thenReturn(0);
        when(bonusRepo.findByIdUserIdAndIdDate(eq(userId), any())).thenReturn(List.of());
        when(resolver.resolve(any())).thenReturn(Archetype.PUSH);
        when(promptBuilder.build(any(), any(), any(), any(), anyInt())).thenReturn("FAKE_PROMPT");

        when(exerciseRepo.findAll()).thenReturn(List.of(
                ex("bench-press"), ex("shoulder-press"), ex("tricep-pushdowns"),
                ex("lateral-raises"), ex("lat-pulldown"), ex("seated-cable-row"),
                ex("bicep-curl"), ex("face-pulls")));
    }

    @Test
    void noOpenAiKey_returnsFallback() {
        service = new BonusSessionService(userRepo, bonusRepo, exerciseRepo, sessionRepo,
                recoveryGate, resolver, promptBuilder, mapper, restTemplate, "");

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED_FALLBACK", response.get("status"));
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void successfulAiResponse_returnsGenerated() {
        mockAiResponse(Map.of(
                "exercises", List.of(
                        Map.of("exerciseId", "bench-press", "sets", 3, "reps", 10,
                                "restSeconds", 90, "suggestedKg", 40.0,
                                "whyThisExercise", "Compound chest",
                                "coachTip", "Pinch shoulder blades"),
                        Map.of("exerciseId", "shoulder-press", "sets", 3, "reps", 10,
                                "restSeconds", 90, "suggestedKg", 15.0,
                                "whyThisExercise", "Overhead power",
                                "coachTip", "Brace core")),
                "sessionNote", "Great week — this bonus is icing",
                "dayCoachTip", "Stay tight"));

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED", response.get("status"));
        assertEquals("PUSH", response.get("archetype"));
        assertEquals("Great week — this bonus is icing", response.get("sessionNote"));
        verify(bonusRepo).save(any(BonusSessionGenerated.class));
    }

    @Test
    void aiReturnsInvalidExerciseId_fallbackUsed() {
        mockAiResponse(Map.of(
                "exercises", List.of(
                        Map.of("exerciseId", "made-up-exercise", "sets", 3, "reps", 10)),
                "sessionNote", "ok",
                "dayCoachTip", "ok"));

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED_FALLBACK", response.get("status"));
        String rationale = (String) response.get("rationale");
        assertTrue(rationale.startsWith("[FALLBACK]"));
    }

    @Test
    void aiReturnsNull_fallbackUsed() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(null);

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED_FALLBACK", response.get("status"));
    }

    @Test
    void aiThrowsException_fallbackUsed() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED_FALLBACK", response.get("status"));
    }

    @Test
    void aiReturnsEmptyExercises_fallbackUsed() {
        mockAiResponse(Map.of(
                "exercises", List.of(),
                "sessionNote", "x", "dayCoachTip", "y"));

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED_FALLBACK", response.get("status"));
    }

    @Test
    void aiReturnsMalformedJson_fallbackUsed() {
        Map<String, Object> apiResponse = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "not valid json {{"))));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(apiResponse);

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED_FALLBACK", response.get("status"));
    }

    @Test
    void markdownCodeFencesAreStrippedFromAiResponse() {
        String wrappedJson = "```json\n{\"exercises\":[{\"exerciseId\":\"bench-press\",\"sets\":3,\"reps\":10}],\"sessionNote\":\"ok\",\"dayCoachTip\":\"ok\"}\n```";
        Map<String, Object> apiResponse = Map.of(
                "choices", List.of(Map.of("message", Map.of("content", wrappedJson))));
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(apiResponse);

        Map<String, Object> response = service.generate(userId);

        assertEquals("GENERATED", response.get("status"));
    }

    @Test
    void weeklyGoalNotHit_throwsForbidden() {
        when(sessionRepo.countByUserIdAndStatusAndSourceNotAndFinishedAtBetween(
                eq(userId), eq("COMPLETED"), eq("BONUS"), any(), any())).thenReturn(2);

        ApiException ex = assertThrows(ApiException.class, () -> service.generate(userId));
        assertTrue(ex.getMessage().contains("Complete your weekly goal"));
    }

    @Test
    void bonusAlreadyExistsForToday_returnsCachedWithoutAiCall() {
        BonusSessionGenerated existing = new BonusSessionGenerated();
        existing.setId(new BonusSessionGeneratedId(userId, LocalDate.now(java.time.ZoneOffset.UTC), 1));
        existing.setArchetype("PUSH");
        existing.setArchetypeRationale("Matched to PUSH based on recovery state.");
        existing.setExercises("[{\"exerciseId\":\"bench-press\",\"sets\":3}]");
        existing.setSessionNote("cached note");
        existing.setDayCoachTip("cached tip");

        when(bonusRepo.findByIdUserIdAndIdDate(eq(userId), any()))
                .thenReturn(List.of(existing));

        Map<String, Object> response = service.generate(userId);

        assertEquals(Boolean.TRUE, response.get("cached"));
        assertEquals("GENERATED", response.get("status"));
        assertEquals("PUSH", response.get("archetype"));
        assertEquals("cached note", response.get("sessionNote"));
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
        verify(bonusRepo, never()).save(any());
    }

    // ── Helpers ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mockAiResponse(Map<String, Object> aiJsonContent) {
        try {
            String content = mapper.writeValueAsString(aiJsonContent);
            Map<String, Object> apiResponse = Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", content))));
            when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(apiResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User buildUser() {
        User u = new User();
        u.setDisplayName("Tester");
        u.setGender("female");
        u.setWeightKg(new BigDecimal("55"));
        u.setHeightCm(new BigDecimal("162"));
        u.setFitnessLevel("INTERMEDIATE");
        u.setGoal("BUILD_MUSCLE");
        u.setWeeklyGoal(4);
        u.setStreak(10);
        u.setHealthConditions(new String[0]);
        return u;
    }

    private Exercise ex(String id) {
        Exercise e = new Exercise();
        try {
            var idField = Exercise.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }
}
