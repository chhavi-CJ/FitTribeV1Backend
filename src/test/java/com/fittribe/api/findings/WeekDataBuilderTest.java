package com.fittribe.api.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fixture-based unit test for {@link WeekDataBuilder}. No Spring, no DB.
 * All repositories mocked. Verifies the builder correctly:
 *
 * <ul>
 *   <li>Parses {@code workout_sessions.exercises} JSONB into per-exercise sets</li>
 *   <li>Parses {@code ai_planned_weights} and populates {@code targetExercises}</li>
 *   <li>Classifies each session as push / pull / legs by dominant volume</li>
 *   <li>Accumulates muscle coverage via the {@link MuscleMapper} smart filter</li>
 *   <li>Pulls previous-week top sets and PR history from mocked repositories</li>
 *   <li>Derives {@code weekNumber} from session metadata with fallback to 1</li>
 *   <li>Computes {@code weeklyGoalHit} from {@code sessionsLogged >= sessionsGoal}</li>
 * </ul>
 */
class WeekDataBuilderTest {

    private WorkoutSessionRepository sessionRepo;
    private SetLogRepository setLogRepo;
    private ExerciseRepository exerciseRepo;
    private UserRepository userRepo;

    private WeekDataBuilder builder;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 6); // Monday
    private static final LocalDate WEEK_END   = LocalDate.of(2026, 4, 13);

    @BeforeEach
    void setUp() {
        sessionRepo  = mock(WorkoutSessionRepository.class);
        setLogRepo   = mock(SetLogRepository.class);
        exerciseRepo = mock(ExerciseRepository.class);
        userRepo     = mock(UserRepository.class);

        builder = new WeekDataBuilder(
                sessionRepo, setLogRepo, exerciseRepo, userRepo,
                new ObjectMapper()
        );

        // User with default weekly_goal = 4
        User user = new User();
        setField(user, "id", USER_ID);
        user.setDisplayName("Asha Rao");
        user.setWeeklyGoal(4);
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));

        // Exercise catalog with 4 seeded rows covering push / pull / legs / core
        when(exerciseRepo.findAll()).thenReturn(List.of(
                exercise("bench-press",    "Bench Press",    "CHEST",     new String[]{"TRICEPS"}, false),
                exercise("lat-pulldown",   "Lat Pulldown",   "BACK",      new String[]{"BICEPS"},  false),
                exercise("squat",          "Squat",          "LEGS",      new String[]{"GLUTES"},  false),
                exercise("plank",          "Plank",          "CORE",      new String[]{},          true)
        ));

        // Default: no previous-week top sets, no all-time previous max
        when(setLogRepo.findTopSetsPerExerciseInWindow(eq(USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(setLogRepo.findAllTimeMaxBeforeForExercises(eq(USER_ID), any(Instant.class), anyCollection()))
                .thenReturn(List.of());
    }

    // ── Test 1: full happy path — 3 sessions, PRs, planned targets ────

    @Test
    @DisplayName("builds WeekData from 3 sessions with PRs, planned targets, and push/pull balance")
    void happyPath() {
        // Session 1 (Mon): push — bench press 60kg, new PR
        WorkoutSession s1 = session(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                instant(2026, 4, 6, 18),  // Monday 6pm UTC
                new BigDecimal("900.00"),
                "[{\"exerciseId\":\"bench-press\",\"exerciseName\":\"Bench Press\"," +
                        "\"sets\":[{\"setNumber\":1,\"reps\":10,\"weightKg\":60.0}," +
                        "{\"setNumber\":2,\"reps\":8,\"weightKg\":60.0}]," +
                        "\"maxWeightKg\":60.0,\"totalVolume\":1080,\"isPr\":true}]",
                "[{\"exerciseId\":\"bench-press\",\"suggestedKg\":55.0}]",
                1
        );

        // Session 2 (Wed): pull — lat pulldown 40kg
        WorkoutSession s2 = session(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                instant(2026, 4, 8, 19),
                new BigDecimal("960.00"),
                "[{\"exerciseId\":\"lat-pulldown\",\"exerciseName\":\"Lat Pulldown\"," +
                        "\"sets\":[{\"setNumber\":1,\"reps\":12,\"weightKg\":40.0}," +
                        "{\"setNumber\":2,\"reps\":12,\"weightKg\":40.0}]," +
                        "\"maxWeightKg\":40.0,\"totalVolume\":960,\"isPr\":false}]",
                "[]",
                1
        );

        // Session 3 (Fri): legs — squat 80kg, new PR
        WorkoutSession s3 = session(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                instant(2026, 4, 10, 7),
                new BigDecimal("1600.00"),
                "[{\"exerciseId\":\"squat\",\"exerciseName\":\"Squat\"," +
                        "\"sets\":[{\"setNumber\":1,\"reps\":10,\"weightKg\":80.0}," +
                        "{\"setNumber\":2,\"reps\":10,\"weightKg\":80.0}]," +
                        "\"maxWeightKg\":80.0,\"totalVolume\":1600,\"isPr\":true}]",
                "[{\"exerciseId\":\"squat\",\"suggestedKg\":75.0}]",
                1
        );

        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"),
                ArgumentMatchers.any(Instant.class),
                ArgumentMatchers.any(Instant.class)))
                .thenReturn(new ArrayList<>(List.of(s1, s2, s3)));

        // Previous all-time max for the two PR exercises (before weekStart)
        when(setLogRepo.findAllTimeMaxBeforeForExercises(
                eq(USER_ID), any(Instant.class), anyCollection()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"bench-press", new BigDecimal("57.5")},
                        new Object[]{"squat",       new BigDecimal("75.0")}
                ));

        // Previous week top sets — lat pulldown was 37.5kg x 10 last week
        when(setLogRepo.findTopSetsPerExerciseInWindow(
                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"lat-pulldown", new BigDecimal("37.5"), 10}
                ));

        WeekData week = builder.build(USER_ID, WEEK_START);

        // Identity + window
        assertThat(week.userId()).isEqualTo(USER_ID);
        assertThat(week.weekStart()).isEqualTo(WEEK_START);
        assertThat(week.weekEnd()).isEqualTo(WEEK_END);
        assertThat(week.weekNumber()).isEqualTo(1);
        assertThat(week.isWeekOne()).isTrue();
        assertThat(week.userFirstName()).isEqualTo("Asha");

        // Headline counts
        assertThat(week.sessionsLogged()).isEqualTo(3);
        assertThat(week.sessionsGoal()).isEqualTo(4);
        assertThat(week.weeklyGoalHit()).isFalse(); // 3 < 4
        assertThat(week.totalKgVolume()).isEqualByComparingTo(new BigDecimal("3460.00"));
        assertThat(week.prCount()).isEqualTo(2); // bench-press + squat

        // Session summaries ordered by finished_at
        assertThat(week.sessions()).hasSize(3);
        assertThat(week.sessions().get(0).sessionId()).isEqualTo(s1.getId());
        assertThat(week.sessions().get(0).isPush()).isTrue();
        assertThat(week.sessions().get(1).isPull()).isTrue();
        assertThat(week.sessions().get(2).isLegs()).isTrue();

        // Push / pull / legs counts
        assertThat(week.pushSessionCount()).isEqualTo(1);
        assertThat(week.pullSessionCount()).isEqualTo(1);
        assertThat(week.legsSessionCount()).isEqualTo(1);

        // setsByExercise
        assertThat(week.setsByExercise()).containsOnlyKeys("bench-press", "lat-pulldown", "squat");
        assertThat(week.setsByExercise().get("bench-press")).hasSize(2);
        assertThat(week.setsByExercise().get("lat-pulldown")).hasSize(2);
        assertThat(week.setsByExercise().get("squat")).hasSize(2);

        // thisWeekTopSets
        assertThat(week.thisWeekTopSets()).hasSize(3);
        assertThat(week.thisWeekTopSets().get("bench-press").weightKg())
                .isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(week.thisWeekTopSets().get("bench-press").reps()).isEqualTo(10);

        // previousWeekTopSets
        assertThat(week.previousWeekTopSets()).hasSize(1);
        assertThat(week.previousWeekTopSets().get("lat-pulldown").weightKg())
                .isEqualByComparingTo(new BigDecimal("37.5"));
        assertThat(week.previousWeekTopSets().get("lat-pulldown").reps()).isEqualTo(10);

        // personalRecords — bench + squat, with previous max
        assertThat(week.personalRecords()).hasSize(2);
        WeekData.PrEntry benchPr = week.personalRecords().stream()
                .filter(p -> p.exerciseId().equals("bench-press")).findFirst().orElseThrow();
        assertThat(benchPr.newMaxKg()).isEqualByComparingTo(new BigDecimal("60.0"));
        assertThat(benchPr.previousMaxKg()).isEqualByComparingTo(new BigDecimal("57.5"));

        // targetExercises from ai_planned_weights
        assertThat(week.targetExercises()).containsKeys("bench-press", "squat");
        WeekData.TargetVsLogged benchTarget = week.targetExercises().get("bench-press");
        assertThat(benchTarget.targetKg()).isEqualByComparingTo(new BigDecimal("55.0"));
        assertThat(benchTarget.loggedMaxKg()).isEqualByComparingTo(new BigDecimal("60.0"));

        // Muscle coverage — CHEST, TRICEPS (bench secondary), BACK_LATS,
        // BICEPS (lat pulldown secondary), LEGS_QUADS
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.CHEST)).isEqualTo(1);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.TRICEPS)).isEqualTo(1);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.BACK_LATS)).isEqualTo(1);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.BICEPS)).isEqualTo(1);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.LEGS_QUADS)).isEqualTo(1);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.HAMSTRINGS)).isEqualTo(0);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.SHOULDERS)).isEqualTo(0);
        assertThat(week.sessionsByMuscle().get(WeeklyReportMuscle.CORE)).isEqualTo(0);

        // Catalog snapshot — every touched exercise has an entry
        assertThat(week.exerciseCatalog()).containsOnlyKeys("bench-press", "lat-pulldown", "squat");
        assertThat(week.exerciseCatalog().get("bench-press").classification())
                .isEqualTo(WeekData.ExerciseMeta.PushPull.PUSH);
        assertThat(week.exerciseCatalog().get("lat-pulldown").classification())
                .isEqualTo(WeekData.ExerciseMeta.PushPull.PULL);
        assertThat(week.exerciseCatalog().get("squat").classification())
                .isEqualTo(WeekData.ExerciseMeta.PushPull.LEGS);
    }

    // ── Test 2: empty week — weeklyGoalHit=false, default week number ─

    @Test
    @DisplayName("zero sessions in window produces empty but well-formed WeekData")
    void emptyWeek() {
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"),
                any(Instant.class), any(Instant.class)))
                .thenReturn(new ArrayList<>());

        WeekData week = builder.build(USER_ID, WEEK_START);

        assertThat(week.sessionsLogged()).isEqualTo(0);
        assertThat(week.sessionsGoal()).isEqualTo(4);
        assertThat(week.weeklyGoalHit()).isFalse();
        assertThat(week.totalKgVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(week.prCount()).isEqualTo(0);
        assertThat(week.weekNumber()).isEqualTo(1);
        assertThat(week.isWeekOne()).isTrue();
        assertThat(week.sessions()).isEmpty();
        assertThat(week.setsByExercise()).isEmpty();
        assertThat(week.personalRecords()).isEmpty();
        assertThat(week.targetExercises()).isEmpty();
        assertThat(week.exerciseCatalog()).isEmpty();

        // sessionsByMuscle has all 8 tiles with 0 counts
        assertThat(week.sessionsByMuscle()).hasSize(8);
        assertThat(week.sessionsByMuscle().values()).allMatch(v -> v == 0);

        // push/pull/legs all zero
        assertThat(week.pushSessionCount()).isEqualTo(0);
        assertThat(week.pullSessionCount()).isEqualTo(0);
        assertThat(week.legsSessionCount()).isEqualTo(0);
    }

    // ── Test 3: goal hit when sessionsLogged >= sessionsGoal ──────────

    @Test
    @DisplayName("weeklyGoalHit is true when sessionsLogged reaches the user's weekly_goal")
    void weeklyGoalHit() {
        // 4 identical push sessions to hit the default goal of 4
        List<WorkoutSession> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(session(
                    UUID.randomUUID(),
                    instant(2026, 4, 6 + i, 10),
                    new BigDecimal("500.00"),
                    "[{\"exerciseId\":\"bench-press\",\"exerciseName\":\"Bench Press\"," +
                            "\"sets\":[{\"setNumber\":1,\"reps\":10,\"weightKg\":50.0}]," +
                            "\"maxWeightKg\":50.0,\"totalVolume\":500,\"isPr\":false}]",
                    "[]",
                    2   // arbitrary week number to verify it propagates
            ));
        }
        when(sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"),
                any(Instant.class), any(Instant.class)))
                .thenReturn(four);

        WeekData week = builder.build(USER_ID, WEEK_START);

        assertThat(week.sessionsLogged()).isEqualTo(4);
        assertThat(week.weeklyGoalHit()).isTrue();
        assertThat(week.weekNumber()).isEqualTo(2);
        assertThat(week.isWeekOne()).isFalse();
        assertThat(week.pushSessionCount()).isEqualTo(4);
    }

    // ── Fixture helpers ────────────────────────────────────────────────

    private static WorkoutSession session(UUID id, Instant finishedAt, BigDecimal totalVolume,
                                          String exercisesJson, String plannedJson,
                                          Integer weekNumber) {
        WorkoutSession ws = new WorkoutSession();
        setField(ws, "id", id);
        ws.setUserId(USER_ID);
        ws.setStatus("COMPLETED");
        ws.setExercises(exercisesJson);
        ws.setAiPlannedWeights(plannedJson);
        ws.setTotalVolumeKg(totalVolume);
        ws.setFinishedAt(finishedAt);
        ws.setWeekNumber(weekNumber);
        return ws;
    }

    private static Exercise exercise(String id, String name, String muscleGroup,
                                     String[] secondaries, boolean bodyweight) {
        Exercise e = new Exercise();
        setField(e, "id", id);
        setField(e, "name", name);
        setField(e, "muscleGroup", muscleGroup);
        setField(e, "secondaryMuscles", secondaries);
        setField(e, "isBodyweight", bodyweight);
        return e;
    }

    private static Instant instant(int y, int m, int d, int hour) {
        return LocalDate.of(y, m, d).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    /** Reflection setter — entity classes use private fields without public setters for PK/immutable cols. */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set " + fieldName, e);
        }
    }
}
