package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecoveryGateServiceTest {

    private WorkoutSessionRepository sessionRepo;
    private ExerciseRepository exerciseRepo;
    private RecoveryGateService service;
    private ObjectMapper mapper;
    private Instant now;
    private UUID userId;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(WorkoutSessionRepository.class);
        exerciseRepo = mock(ExerciseRepository.class);
        mapper = new ObjectMapper();
        service = new RecoveryGateService(sessionRepo, exerciseRepo, mapper);
        now = Instant.parse("2026-04-15T12:00:00Z");
        userId = UUID.randomUUID();

        // Default: catalog has bench-press (CHEST), barbell-row (BACK),
        // squat (LEGS, secondaries GLUTES/HAMSTRINGS), burpees (FULL_BODY,
        // secondaries CHEST/SHOULDERS/LEGS), lateral-raises (SHOULDERS)
        when(exerciseRepo.findAll()).thenReturn(List.of(
                exercise("bench-press", "CHEST", null),
                exercise("barbell-row", "BACK", new String[]{"Biceps", "Rear delts"}),
                exercise("squat", "LEGS", new String[]{"Glutes", "Hamstrings"}),
                exercise("burpees", "FULL_BODY", new String[]{"Chest", "Shoulders", "Legs"}),
                exercise("lateral-raises", "SHOULDERS", null)
        ));
    }

    @Test
    void noSessions_returnsEmptyMap() {
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any())).thenReturn(List.of());

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertTrue(result.isEmpty());
    }

    @Test
    void sessionUnder48hAgo_marksAllHitMusclesAsCooked() {
        Instant sessionFinish = now.minus(20, ChronoUnit.HOURS);
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(
                session("bench-press", sessionFinish)));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("CHEST"));
    }

    @Test
    void sessionBetween48and72hAgo_marksMusclesAsReady() {
        Instant sessionFinish = now.minus(60, ChronoUnit.HOURS);
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(
                session("bench-press", sessionFinish)));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertEquals(RecoveryGateService.RecoveryState.READY, result.get("CHEST"));
    }

    @Test
    void sessionOver72hAgo_marksMusclesAsFresh() {
        Instant sessionFinish = now.minus(80, ChronoUnit.HOURS);
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(
                session("bench-press", sessionFinish)));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertEquals(RecoveryGateService.RecoveryState.FRESH, result.get("CHEST"));
    }

    @Test
    void fullBodyPrimary_notAddedAsFullBody_butSecondariesContribute() {
        Instant sessionFinish = now.minus(10, ChronoUnit.HOURS);
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(
                session("burpees", sessionFinish)));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertNull(result.get("FULL_BODY"),
                "FULL_BODY primary should not be tracked directly");
        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("CHEST"));
        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("SHOULDERS"));
        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("LEGS"));
    }

    @Test
    void secondaryMuscles_contributeToRecoveryTracking() {
        Instant sessionFinish = now.minus(10, ChronoUnit.HOURS);
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(
                session("barbell-row", sessionFinish)));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("BACK"));
        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("BICEPS"));
        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("SHOULDERS"));
    }

    @Test
    void mostRecentSessionWins_whenMultipleSessionsHitSameMuscle() {
        Instant older = now.minus(80, ChronoUnit.HOURS);     // FRESH if alone
        Instant newer = now.minus(10, ChronoUnit.HOURS);     // COOKED if alone
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(
                session("bench-press", older),
                session("bench-press", newer)));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertEquals(RecoveryGateService.RecoveryState.COOKED, result.get("CHEST"),
                "More recent session should determine state");
    }

    @Test
    void unknownExerciseId_isSkippedGracefully() {
        Instant sessionFinish = now.minus(10, ChronoUnit.HOURS);
        WorkoutSession s = rawSession(
                "[{\"exerciseId\":\"made-up-exercise\"}]", sessionFinish);
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(s));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertTrue(result.isEmpty());
    }

    @Test
    void malformedJsonb_doesNotThrow_returnsEmpty() {
        WorkoutSession s = rawSession("not-valid-json", now.minus(10, ChronoUnit.HOURS));
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(s));

        assertDoesNotThrow(() -> service.computeRecoveryState(userId, now));
    }

    @Test
    void nullExercisesJsonb_isSkipped() {
        WorkoutSession s = rawSession(null, now.minus(10, ChronoUnit.HOURS));
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(List.of(s));

        Map<String, RecoveryGateService.RecoveryState> result =
                service.computeRecoveryState(userId, now);

        assertTrue(result.isEmpty());
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Exercise exercise(String id, String muscleGroup, String[] secondaries) {
        Exercise e = new Exercise();
        // Using reflection-free setters assumed to exist on Exercise entity.
        // If Exercise has no public setters, this helper may need adjustment
        // after inspecting Exercise.java — see compile output.
        try {
            var idField = Exercise.class.getDeclaredField("id");
            idField.setAccessible(true); idField.set(e, id);
            var mgField = Exercise.class.getDeclaredField("muscleGroup");
            mgField.setAccessible(true); mgField.set(e, muscleGroup);
            var secField = Exercise.class.getDeclaredField("secondaryMuscles");
            secField.setAccessible(true); secField.set(e, secondaries);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    private WorkoutSession session(String exerciseId, Instant finishedAt) {
        String json = "[{\"exerciseId\":\"" + exerciseId + "\"}]";
        return rawSession(json, finishedAt);
    }

    private WorkoutSession rawSession(String exercisesJson, Instant finishedAt) {
        WorkoutSession s = new WorkoutSession();
        s.setExercises(exercisesJson);
        s.setFinishedAt(finishedAt);
        return s;
    }
}
