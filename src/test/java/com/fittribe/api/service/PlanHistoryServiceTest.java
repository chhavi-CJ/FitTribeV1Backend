package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanHistoryServiceTest {

    @Mock WorkoutSessionRepository sessionRepo;
    @Mock PrEventRepository        prEventRepo;

    PlanHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PlanHistoryService(sessionRepo, prEventRepo, new ObjectMapper());
    }

    // ── 6a) Empty when no sessions in window ─────────────────────────────

    @Test
    void returnsEmptyWhenNoSessionsInWindow() {
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of());

        List<HistoricalSet> result = service.getRecentLoggedSets(
                UUID.randomUUID(), Instant.now().minus(14, ChronoUnit.DAYS), Map.of());

        assertThat(result).isEmpty();
    }

    // ── 6b) Parses JSONB into HistoricalSet records ──────────────────────

    @Test
    void parsesJsonbIntoHistoricalSetRecords() {
        UUID sessionId = UUID.randomUUID();
        UUID setId     = UUID.randomUUID();

        WorkoutSession session = sessionWith(sessionId, Instant.now().minus(1, ChronoUnit.DAYS),
                "[{\"exerciseId\":\"bench-press\",\"exerciseName\":\"Bench Press\"," +
                "\"sets\":[{\"setId\":\"" + setId + "\",\"setNumber\":1," +
                "\"weightKg\":60,\"reps\":8}]}]");

        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(session));
        when(prEventRepo.findByUserIdAndSessionIdInAndWeekStartInAndSupersededAtIsNull(
                any(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        Exercise exercise = exerciseWith("bench-press", "Bench Press", "Chest");
        Map<String, Exercise> exMap = Map.of("bench-press", exercise);

        List<HistoricalSet> result = service.getRecentLoggedSets(
                UUID.randomUUID(), Instant.now().minus(14, ChronoUnit.DAYS), exMap);

        assertThat(result).hasSize(1);
        HistoricalSet hs = result.get(0);
        assertThat(hs.exerciseId()).isEqualTo("bench-press");
        assertThat(hs.weightKg().compareTo(new BigDecimal("60"))).isZero();
        assertThat(hs.reps()).isEqualTo(8);
        assertThat(hs.isPr()).isFalse();
        assertThat(hs.muscleGroup()).isEqualTo("Chest");
    }

    // ── 6c) Marks set as PR when pr_events contains its set_id ──────────

    @Test
    void marksSetAsPrWhenPrEventMatchesSetId() {
        UUID sessionId = UUID.randomUUID();
        UUID setId     = UUID.randomUUID();

        WorkoutSession session = sessionWith(sessionId, Instant.now().minus(1, ChronoUnit.DAYS),
                "[{\"exerciseId\":\"bench-press\",\"exerciseName\":\"Bench Press\"," +
                "\"sets\":[{\"setId\":\"" + setId + "\",\"setNumber\":1," +
                "\"weightKg\":65,\"reps\":8}]}]");

        PrEvent pr = prEventWith(sessionId, setId, "WEIGHT_PR");

        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(session));
        when(prEventRepo.findByUserIdAndSessionIdInAndWeekStartInAndSupersededAtIsNull(
                any(), anyCollection(), anyCollection()))
                .thenReturn(List.of(pr));

        List<HistoricalSet> result = service.getRecentLoggedSets(
                UUID.randomUUID(), Instant.now().minus(14, ChronoUnit.DAYS), Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isPr()).isTrue();
    }

    // ── 6d) FIRST_EVER excluded from isPr ───────────────────────────────

    @Test
    void ignoresFirstEverPrEventsForStallDetection() {
        UUID sessionId = UUID.randomUUID();
        UUID setId     = UUID.randomUUID();

        WorkoutSession session = sessionWith(sessionId, Instant.now().minus(1, ChronoUnit.DAYS),
                "[{\"exerciseId\":\"squat\",\"exerciseName\":\"Squat\"," +
                "\"sets\":[{\"setId\":\"" + setId + "\",\"setNumber\":1," +
                "\"weightKg\":100,\"reps\":5}]}]");

        PrEvent firstEver = prEventWith(sessionId, setId, "FIRST_EVER");

        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(session));
        when(prEventRepo.findByUserIdAndSessionIdInAndWeekStartInAndSupersededAtIsNull(
                any(), anyCollection(), anyCollection()))
                .thenReturn(List.of(firstEver));

        List<HistoricalSet> result = service.getRecentLoggedSets(
                UUID.randomUUID(), Instant.now().minus(14, ChronoUnit.DAYS), Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isPr()).isFalse();
    }

    // ── 6e) Malformed JSONB tolerated without exception ──────────────────

    @Test
    void toleratesMalformedJsonbWithoutCrashing() {
        UUID sessionId = UUID.randomUUID();

        WorkoutSession session = sessionWith(sessionId, Instant.now().minus(1, ChronoUnit.DAYS),
                "not json");

        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(session));
        when(prEventRepo.findByUserIdAndSessionIdInAndWeekStartInAndSupersededAtIsNull(
                any(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<HistoricalSet> result = service.getRecentLoggedSets(
                UUID.randomUUID(), Instant.now().minus(14, ChronoUnit.DAYS), Map.of());

        assertThat(result).isEmpty();
    }

    // ── 6f) Unknown exerciseId yields empty muscleGroup, no exception ────

    @Test
    void muscleGroupEmptyWhenExerciseIdNotInCatalog() {
        UUID sessionId = UUID.randomUUID();
        UUID setId     = UUID.randomUUID();

        WorkoutSession session = sessionWith(sessionId, Instant.now().minus(1, ChronoUnit.DAYS),
                "[{\"exerciseId\":\"unknown-exercise\"," +
                "\"sets\":[{\"setId\":\"" + setId + "\",\"setNumber\":1," +
                "\"weightKg\":50,\"reps\":10}]}]");

        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(session));
        when(prEventRepo.findByUserIdAndSessionIdInAndWeekStartInAndSupersededAtIsNull(
                any(), anyCollection(), anyCollection()))
                .thenReturn(List.of());

        List<HistoricalSet> result = service.getRecentLoggedSets(
                UUID.randomUUID(), Instant.now().minus(14, ChronoUnit.DAYS), Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).muscleGroup()).isEqualTo("");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private WorkoutSession sessionWith(UUID id, Instant finishedAt, String exercisesJson) {
        WorkoutSession s = new WorkoutSession();
        setField(s, WorkoutSession.class, "id", id);
        s.setUserId(UUID.randomUUID());
        s.setStatus("COMPLETED");
        s.setFinishedAt(finishedAt);
        s.setExercises(exercisesJson);
        return s;
    }

    private Exercise exerciseWith(String id, String name, String muscleGroup) {
        Exercise ex = new Exercise();
        setField(ex, Exercise.class, "id", id);
        setField(ex, Exercise.class, "name", name);
        setField(ex, Exercise.class, "muscleGroup", muscleGroup);
        return ex;
    }

    private static void setField(Object target, Class<?> clazz, String field, Object value) {
        try {
            var f = clazz.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PrEvent prEventWith(UUID sessionId, UUID setId, String category) {
        PrEvent pe = new PrEvent();
        pe.setSessionId(sessionId);
        pe.setSetId(setId);
        pe.setPrCategory(category);
        return pe;
    }
}
