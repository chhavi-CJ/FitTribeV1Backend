package com.fittribe.api.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WeeklyReportCron}. {@link UserRepository} is
 * a JPA repository interface and so mockable normally;
 * {@link JobEnqueuer} is a concrete class that Mockito on JDK 25 can't
 * instrument, so we use a hand-rolled fake that records each enqueue
 * — same pattern as {@code JobWorkerTest#FakeWeeklyReportComputer}.
 */
class WeeklyReportCronTest {

    private UserRepository userRepo;
    private FakeJobEnqueuer jobEnqueuer;
    private WeeklyReportCron cron;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        jobEnqueuer = new FakeJobEnqueuer();
        cron = new WeeklyReportCron(userRepo, jobEnqueuer);
    }

    // ── Happy path: one enqueue per active user ─────────────────────────

    @Test
    void fanOutEnqueuesOneJobPerActiveUser() {
        UUID u1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
        when(userRepo.findActiveUserIds()).thenReturn(List.of(u1, u2, u3));

        LocalDate expectedWeekStart = JobWorker.previousMondayIst();
        String expectedWeekStartIso = expectedWeekStart.toString();

        cron.run();

        assertEquals(3, jobEnqueuer.calls.size(), "one enqueue per user");

        // All rows are COMPUTE_WEEKLY_REPORT
        for (FakeJobEnqueuer.Call c : jobEnqueuer.calls) {
            assertEquals(JobType.COMPUTE_WEEKLY_REPORT, c.type);
        }

        // Payload shape per row
        assertPayload(jobEnqueuer.calls.get(0), u1, expectedWeekStartIso);
        assertPayload(jobEnqueuer.calls.get(1), u2, expectedWeekStartIso);
        assertPayload(jobEnqueuer.calls.get(2), u3, expectedWeekStartIso);
    }

    // ── Zero active users → logs 0 and does not throw ───────────────────

    @Test
    void zeroActiveUsersIsNoOp() {
        when(userRepo.findActiveUserIds()).thenReturn(List.of());

        cron.run(); // must not throw

        assertTrue(jobEnqueuer.calls.isEmpty(), "no jobs should be enqueued for empty cohort");
    }

    // ── A single enqueue failure must not abort the rest ────────────────

    @Test
    void individualEnqueueFailureDoesNotAbortCohort() {
        UUID u1 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
        when(userRepo.findActiveUserIds()).thenReturn(List.of(u1, u2, u3));

        // Throw on the second user only.
        jobEnqueuer.throwOn = u2;

        cron.run(); // must not throw

        // All three were attempted; the middle one failed.
        assertEquals(3, jobEnqueuer.calls.size(), "all three users must be attempted");
        assertEquals(u1, jobEnqueuer.calls.get(0).userIdArg);
        assertEquals(u2, jobEnqueuer.calls.get(1).userIdArg);
        assertEquals(u3, jobEnqueuer.calls.get(2).userIdArg);
    }

    // ── Cron queries the active cohort exactly once ─────────────────────

    @Test
    void userRepositoryQueriedOnce() {
        when(userRepo.findActiveUserIds()).thenReturn(List.of());
        cron.run();
        verify(userRepo, times(1)).findActiveUserIds();
    }

    // ── No user iteration when repo returns empty list ──────────────────

    @Test
    void emptyCohortNeverTouchesEnqueuer() {
        when(userRepo.findActiveUserIds()).thenReturn(List.of());
        cron.run();
        // No direct interaction on the fake at all.
        assertTrue(jobEnqueuer.calls.isEmpty());
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private static void assertPayload(FakeJobEnqueuer.Call c, UUID expectedUser, String expectedWeekStartIso) {
        Map<String, Object> payload = c.payload;
        assertEquals(expectedUser.toString(), payload.get("userId"),
                "payload.userId must be the user uuid as string");
        assertEquals(expectedWeekStartIso, payload.get("weekStart"),
                "payload.weekStart must be the ISO date of the previous IST Monday");
        assertEquals(2, payload.size(), "payload must contain exactly userId + weekStart");
    }

    // ── Fake JobEnqueuer (Mockito can't instrument the concrete class on JDK 25) ──

    static final class FakeJobEnqueuer extends JobEnqueuer {
        final List<Call> calls = new ArrayList<>();
        /** Optional: when set, any enqueue for this user id throws. */
        UUID throwOn;

        FakeJobEnqueuer() {
            super(null, new ObjectMapper());
        }

        @Override
        public Long enqueue(JobType type, Map<String, Object> payload) {
            UUID userId = UUID.fromString((String) payload.get("userId"));
            Call c = new Call(type, userId, Map.copyOf(payload));
            calls.add(c);
            if (throwOn != null && throwOn.equals(userId)) {
                throw new RuntimeException("simulated enqueue failure for " + userId);
            }
            // Return a fake id.
            return (long) calls.size();
        }

        static final class Call {
            final JobType type;
            final UUID userIdArg;
            final Map<String, Object> payload;
            Call(JobType type, UUID userIdArg, Map<String, Object> payload) {
                this.type = type;
                this.userIdArg = userIdArg;
                this.payload = payload;
            }
        }
    }
}
