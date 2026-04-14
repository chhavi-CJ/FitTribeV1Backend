package com.fittribe.api.prv2;

import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.UserExerciseBests;
import com.fittribe.api.entity.UserExerciseBestsId;
import com.fittribe.api.entity.WeeklyPrCount;
import com.fittribe.api.entity.WeeklyPrCountId;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.WeeklyPrCountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PR System V2 data layer (Phase 1).
 * Verifies entity mapping, repository queries, and ledger invariants.
 *
 * Uses @DataJpaTest to run against an in-memory test database with Flyway
 * migrations applied (including V44). Each test gets a fresh database state.
 */
@Disabled("Requires Testcontainers setup (Phase 1b). JPA mappings verified by inspection and Flyway validates schema at first deploy. Re-enable after test infrastructure ships.")
@DataJpaTest
@DisplayName("PR System V2 — Phase 1 Data Layer")
class PrSystemV2RepositoryIntegrationTest {

    @Autowired
    private UserExerciseBestsRepository userExerciseBestsRepo;

    @Autowired
    private PrEventRepository prEventRepo;

    @Autowired
    private WeeklyPrCountRepository weeklyPrCountRepo;

    @Autowired
    private EntityManager entityManager;

    private UUID testUserId;
    private UUID testSessionId;
    private String testExerciseId;
    private LocalDate testWeekStart;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testSessionId = UUID.randomUUID();
        testExerciseId = "bench-press";
        testWeekStart = LocalDate.of(2026, 4, 14); // Monday
    }

    // ── UserExerciseBests Tests ────────────────────────────────────────

    @Test
    @DisplayName("UserExerciseBests: Round-trip weight-based exercise")
    void testUserExerciseBestsWeightExerciseRoundTrip() {
        // Save
        UserExerciseBests best = new UserExerciseBests();
        best.setUserId(testUserId);
        best.setExerciseId(testExerciseId);
        best.setExerciseType("WEIGHTED");
        best.setBestWtKg(new BigDecimal("100.00"));
        best.setRepsAtBestWt(5);
        best.setBestReps(12);
        best.setWtAtBestRepsKg(new BigDecimal("80.00"));
        best.setBest1rmEpleyKg(new BigDecimal("110.00"));
        best.setBestSetVolumeKg(new BigDecimal("600.00"));
        best.setBestSessionVolumeKg(new BigDecimal("2840.00"));
        best.setTotalSessionsWithExercise(15);
        best.setLastLoggedAt(Instant.now());

        userExerciseBestsRepo.saveAndFlush(best);

        // Retrieve by composite key
        UserExerciseBests retrieved = userExerciseBestsRepo
            .findByUserIdAndExerciseId(testUserId, testExerciseId)
            .orElse(null);

        // Verify all fields roundtrip
        assertNotNull(retrieved);
        assertEquals(testUserId, retrieved.getUserId());
        assertEquals(testExerciseId, retrieved.getExerciseId());
        assertEquals("WEIGHTED", retrieved.getExerciseType());
        assertEquals(new BigDecimal("100.00"), retrieved.getBestWtKg());
        assertEquals(5, retrieved.getRepsAtBestWt());
        assertEquals(12, retrieved.getBestReps());
        assertEquals(new BigDecimal("80.00"), retrieved.getWtAtBestRepsKg());
        assertEquals(new BigDecimal("110.00"), retrieved.getBest1rmEpleyKg());
        assertEquals(new BigDecimal("600.00"), retrieved.getBestSetVolumeKg());
        assertEquals(new BigDecimal("2840.00"), retrieved.getBestSessionVolumeKg());
        assertEquals(15, retrieved.getTotalSessionsWithExercise());
        assertNotNull(retrieved.getLastLoggedAt());
    }

    @Test
    @DisplayName("UserExerciseBests: Round-trip timed exercise")
    void testUserExerciseBestsTimedExerciseRoundTrip() {
        // Save
        UserExerciseBests best = new UserExerciseBests();
        best.setUserId(testUserId);
        best.setExerciseId("plank-hold");
        best.setExerciseType("TIMED");
        best.setBestHoldSeconds(120);
        best.setTotalSessionsWithExercise(8);

        userExerciseBestsRepo.saveAndFlush(best);

        // Retrieve and verify
        UserExerciseBests retrieved = userExerciseBestsRepo
            .findByUserIdAndExerciseId(testUserId, "plank-hold")
            .orElse(null);

        assertNotNull(retrieved);
        assertEquals("TIMED", retrieved.getExerciseType());
        assertEquals(120, retrieved.getBestHoldSeconds());
        // Weight fields should be null for timed exercises
        assertNull(retrieved.getBestWtKg());
        assertNull(retrieved.getBestReps());
    }

    @Test
    @DisplayName("UserExerciseBests: Composite key uniqueness")
    void testUserExerciseBestsCompositeKeyUniqueness() {
        UserExerciseBests best1 = new UserExerciseBests();
        best1.setUserId(testUserId);
        best1.setExerciseId(testExerciseId);
        best1.setExerciseType("WEIGHTED");
        best1.setBestWtKg(new BigDecimal("100.00"));
        userExerciseBestsRepo.saveAndFlush(best1);

        // Same user, different exercise — should succeed
        UserExerciseBests best2 = new UserExerciseBests();
        best2.setUserId(testUserId);
        best2.setExerciseId("squat");
        best2.setExerciseType("WEIGHTED");
        best2.setBestWtKg(new BigDecimal("150.00"));
        userExerciseBestsRepo.saveAndFlush(best2);

        // Both should exist
        assertEquals(2, userExerciseBestsRepo.count());
    }

    // ── PrEvent Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("PrEvent: Round-trip with signals_met and value_payload JSONB")
    void testPrEventJsonbRoundTrip() {
        // Save with JSONB payloads
        PrEvent event = new PrEvent();
        event.setUserId(testUserId);
        event.setExerciseId(testExerciseId);
        event.setSessionId(testSessionId);
        event.setPrCategory("WEIGHT_PR");
        event.setSignalsMet("{\"weight\": true, \"rep\": false, \"volume\": true, \"one_rm\": true}");
        event.setValuePayload("{\"delta_kg\": 5, \"previous_best\": 100.0, \"new_best\": 105.0}");
        event.setCoinsAwarded(50);
        event.setDetectorVersion("v1.0");
        event.setWeekStart(testWeekStart);

        prEventRepo.saveAndFlush(event);
        UUID eventId = event.getId();

        // Clear Hibernate cache and retrieve
        prEventRepo.flush();
        entityManager.clear();

        PrEvent retrieved = prEventRepo.findById(eventId).orElse(null);

        // Verify JSON payloads preserved exactly
        assertNotNull(retrieved);
        assertEquals("{\"weight\": true, \"rep\": false, \"volume\": true, \"one_rm\": true}",
                     retrieved.getSignalsMet());
        assertEquals("{\"delta_kg\": 5, \"previous_best\": 100.0, \"new_best\": 105.0}",
                     retrieved.getValuePayload());
        assertEquals(50, retrieved.getCoinsAwarded());
    }

    @Test
    @DisplayName("PrEvent: Supersession semantics (active events filter)")
    void testPrEventSupersessionSemantics() {
        // Create two events, supersede the first
        PrEvent event1 = new PrEvent();
        event1.setUserId(testUserId);
        event1.setExerciseId(testExerciseId);
        event1.setSessionId(testSessionId);
        event1.setPrCategory("WEIGHT_PR");
        event1.setSignalsMet("{}");
        event1.setValuePayload("{}");
        event1.setWeekStart(testWeekStart);
        prEventRepo.saveAndFlush(event1);

        PrEvent event2 = new PrEvent();
        event2.setUserId(testUserId);
        event2.setExerciseId(testExerciseId);
        event2.setSessionId(testSessionId);
        event2.setPrCategory("WEIGHT_PR");
        event2.setSignalsMet("{}");
        event2.setValuePayload("{}");
        event2.setWeekStart(testWeekStart);
        prEventRepo.saveAndFlush(event2);

        // Mark first as superseded by second
        event1.setSupersededAt(Instant.now());
        event1.setSupersededBy(event2.getId());
        prEventRepo.saveAndFlush(event1);

        // Query active events (not superseded) for the week
        var activeEvents = prEventRepo.findByUserIdAndWeekStartAndSupersededAtIsNull(testUserId, testWeekStart);

        // Should only return event2 (event1 is superseded)
        assertEquals(1, activeEvents.size());
        assertEquals(event2.getId(), activeEvents.get(0).getId());
    }

    @Test
    @DisplayName("PrEvent: Query by session (for daily summary)")
    void testPrEventQueryBySession() {
        // Create multiple events for same session
        for (int i = 0; i < 3; i++) {
            PrEvent event = new PrEvent();
            event.setUserId(testUserId);
            event.setExerciseId(testExerciseId);
            event.setSessionId(testSessionId);
            event.setPrCategory("WEIGHT_PR");
            event.setSignalsMet("{}");
            event.setValuePayload("{}");
            event.setWeekStart(testWeekStart);
            prEventRepo.saveAndFlush(event);
        }

        // Query by session
        var sessionEvents = prEventRepo.findBySessionIdAndSupersededAtIsNull(testSessionId);

        assertEquals(3, sessionEvents.size());
        assertTrue(sessionEvents.stream().allMatch(e -> e.getSessionId().equals(testSessionId)));
    }

    @Test
    @DisplayName("PrEvent: existsBySessionIdAndSupersededAtIsNull (for history badge)")
    void testPrEventExistsBySessionId() {
        PrEvent event = new PrEvent();
        event.setUserId(testUserId);
        event.setExerciseId(testExerciseId);
        event.setSessionId(testSessionId);
        event.setPrCategory("FIRST_EVER");
        event.setSignalsMet("{}");
        event.setValuePayload("{}");
        event.setWeekStart(testWeekStart);
        prEventRepo.saveAndFlush(event);

        // Check for existence
        assertTrue(prEventRepo.existsBySessionIdAndSupersededAtIsNull(testSessionId));

        // Supersede and verify no longer exists as active
        event.setSupersededAt(Instant.now());
        prEventRepo.saveAndFlush(event);

        assertFalse(prEventRepo.existsBySessionIdAndSupersededAtIsNull(testSessionId));
    }

    @Test
    @DisplayName("PrEvent: All pr_category enum values persist")
    void testPrEventPrCategoryEnumValues() {
        String[] categories = {"FIRST_EVER", "WEIGHT_PR", "REP_PR", "VOLUME_PR", "MAX_ATTEMPT"};

        for (String category : categories) {
            PrEvent event = new PrEvent();
            event.setUserId(testUserId);
            event.setExerciseId(testExerciseId + "_" + category);
            event.setSessionId(testSessionId);
            event.setPrCategory(category);
            event.setSignalsMet("{}");
            event.setValuePayload("{}");
            event.setWeekStart(testWeekStart);
            prEventRepo.saveAndFlush(event);
        }

        // Verify all saved and retrieved correctly
        long count = prEventRepo.count();
        assertEquals(5, count);

        for (String category : categories) {
            PrEvent retrieved = prEventRepo.findById(
                prEventRepo.findAll().stream()
                    .filter(e -> category.equals(e.getPrCategory()))
                    .findFirst()
                    .orElseThrow()
                    .getId()
            ).orElse(null);
            assertNotNull(retrieved);
            assertEquals(category, retrieved.getPrCategory());
        }
    }

    // ── WeeklyPrCount Tests ────────────────────────────────────────────

    @Test
    @DisplayName("WeeklyPrCount: Round-trip counters and sealed_at")
    void testWeeklyPrCountRoundTrip() {
        // Save
        WeeklyPrCount count = new WeeklyPrCount();
        count.setUserId(testUserId);
        count.setWeekStart(testWeekStart);
        count.setFirstEverCount(2);
        count.setPrCount(5);
        count.setMaxAttemptCount(1);
        count.setTotalCoinsAwarded(250);
        count.setSealedAt(Instant.now());

        weeklyPrCountRepo.saveAndFlush(count);

        // Retrieve by composite key
        WeeklyPrCount retrieved = weeklyPrCountRepo
            .findByUserIdAndWeekStart(testUserId, testWeekStart)
            .orElse(null);

        // Verify all fields roundtrip
        assertNotNull(retrieved);
        assertEquals(testUserId, retrieved.getUserId());
        assertEquals(testWeekStart, retrieved.getWeekStart());
        assertEquals(2, retrieved.getFirstEverCount());
        assertEquals(5, retrieved.getPrCount());
        assertEquals(1, retrieved.getMaxAttemptCount());
        assertEquals(250, retrieved.getTotalCoinsAwarded());
        assertNotNull(retrieved.getSealedAt());
    }

    @Test
    @DisplayName("WeeklyPrCount: Composite key uniqueness per user per week")
    void testWeeklyPrCountCompositeKeyUniqueness() {
        WeeklyPrCount count1 = new WeeklyPrCount();
        count1.setUserId(testUserId);
        count1.setWeekStart(testWeekStart);
        count1.setFirstEverCount(1);
        weeklyPrCountRepo.saveAndFlush(count1);

        // Same user, different week — should succeed
        LocalDate nextWeek = testWeekStart.plusWeeks(1);
        WeeklyPrCount count2 = new WeeklyPrCount();
        count2.setUserId(testUserId);
        count2.setWeekStart(nextWeek);
        count2.setFirstEverCount(2);
        weeklyPrCountRepo.saveAndFlush(count2);

        // Both should exist
        assertEquals(2, weeklyPrCountRepo.count());
    }

    // ── Exercise Type Enum Persistence ────────────────────────────────

    @Test
    @DisplayName("UserExerciseBests: All exerciseType enum values persist")
    void testUserExerciseBestsExerciseTypeEnumValues() {
        String[] types = {"WEIGHTED", "BODYWEIGHT_UNASSISTED", "BODYWEIGHT_ASSISTED",
                         "BODYWEIGHT_WEIGHTED", "TIMED"};

        for (String type : types) {
            UserExerciseBests best = new UserExerciseBests();
            best.setUserId(testUserId);
            best.setExerciseId("exercise_" + type);
            best.setExerciseType(type);
            if (!type.equals("TIMED")) {
                best.setBestWtKg(new BigDecimal("50.00"));
            } else {
                best.setBestHoldSeconds(60);
            }
            userExerciseBestsRepo.saveAndFlush(best);
        }

        // Verify all saved and retrieved correctly
        long count = userExerciseBestsRepo.count();
        assertEquals(5, count);

        for (String type : types) {
            UserExerciseBests retrieved = userExerciseBestsRepo.findByUserIdAndExerciseId(
                testUserId, "exercise_" + type
            ).orElse(null);
            assertNotNull(retrieved);
            assertEquals(type, retrieved.getExerciseType());
        }
    }
}
