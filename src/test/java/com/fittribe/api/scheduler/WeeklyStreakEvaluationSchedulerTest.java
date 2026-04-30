package com.fittribe.api.scheduler;

import com.fittribe.api.entity.BonusFreezeGrant;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.BonusFreezeGrantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyStreakEvaluationSchedulerTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private WorkoutSessionRepository sessionRepo;

    @Mock
    private BonusFreezeGrantService bonusFreezeGrantService;

    @InjectMocks
    private WeeklyStreakEvaluationScheduler scheduler;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a User with the given fields. createdAt is set via reflection
     * because the JPA field is insertable=false/updatable=false — no setter exists.
     */
    private User makeUser(UUID id, int streak, int weeklyGoal,
                          int purchasedFreezeBalance, int accountAgeDays) {
        User user = new User();
        user.setId(id);
        user.setStreak(streak);
        user.setWeeklyGoal(weeklyGoal);
        user.setPurchasedFreezeBalance(purchasedFreezeBalance);
        // Set createdAt via reflection — field has no setter (insertable/updatable = false)
        Instant createdAt = Instant.now().minus(accountAgeDays, ChronoUnit.DAYS);
        try {
            Field f = User.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(user, createdAt);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not set createdAt on User via reflection", e);
        }
        return user;
    }

    /**
     * Builds a BonusFreezeGrant that is already valid (validFrom in the past)
     * and expires in the future.
     */
    private BonusFreezeGrant makeActiveGrant(long id, int daysUntilExpiry) {
        BonusFreezeGrant g = new BonusFreezeGrant();
        g.setId(id);
        g.setExpiresAt(Instant.now().plus(daysUntilExpiry, ChronoUnit.DAYS));
        g.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));  // already valid
        g.setConsumedAt(null);
        return g;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * TEST 1: User meets their weekly goal exactly.
     * No freezes should be spent and no save should occur.
     */
    @Test
    void testGoalMet_streakSurvives_noFreezesSpent() {
        UUID userId = UUID.randomUUID();
        User user = makeUser(userId, 5, 5, 3, 30);

        when(userRepo.findAllByStreakGreaterThan(0)).thenReturn(List.of(user));
        when(sessionRepo.countDistinctCompletedDaysInRange(
                eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(5L);
        // No stub for getActiveGrants — the scheduler short-circuits at shortfall==0
        // and never reaches the freeze-spending block.

        scheduler.evaluateWeeklyStreaks();

        verify(userRepo, never()).save(any());
        verify(bonusFreezeGrantService, never()).consumeGrant(any(), any());
    }

    /**
     * TEST 2: Shortfall of 1, covered by a single bonus freeze grant.
     * Streak survives; purchased balance is untouched; no userRepo.save needed.
     */
    @Test
    void testShortfallOfOne_oneBonusFreezeAvailable_streakSurvives() {
        UUID userId = UUID.randomUUID();
        User user = makeUser(userId, 10, 5, 0, 30);
        BonusFreezeGrant grant1 = makeActiveGrant(1L, 5);

        when(userRepo.findAllByStreakGreaterThan(0)).thenReturn(List.of(user));
        when(sessionRepo.countDistinctCompletedDaysInRange(
                eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(4L); // shortfall = 1
        when(bonusFreezeGrantService.getActiveGrants(userId)).thenReturn(List.of(grant1));

        scheduler.evaluateWeeklyStreaks();

        verify(bonusFreezeGrantService).consumeGrant(grant1, "AUTO_APPLY");
        // purchasedSpent == 0, so no save required
        verify(userRepo, never()).save(any());
        assertThat(user.getStreak()).isEqualTo(10);
    }

    /**
     * TEST 3: Shortfall of 2 — covered by 1 bonus freeze + 1 purchased freeze.
     * Bonus freeze is spent first, then purchased balance decremented by 1.
     * userRepo.save IS called because purchasedFreezeBalance changed.
     */
    @Test
    void testShortfallOfTwo_oneBonus_onePurchased_bothSpentBonusFirst() {
        UUID userId = UUID.randomUUID();
        User user = makeUser(userId, 7, 5, 5, 30);
        BonusFreezeGrant grant1 = makeActiveGrant(1L, 5);

        when(userRepo.findAllByStreakGreaterThan(0)).thenReturn(List.of(user));
        when(sessionRepo.countDistinctCompletedDaysInRange(
                eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(3L); // shortfall = 2
        when(bonusFreezeGrantService.getActiveGrants(userId)).thenReturn(List.of(grant1));

        scheduler.evaluateWeeklyStreaks();

        // Bonus first
        verify(bonusFreezeGrantService).consumeGrant(grant1, "AUTO_APPLY");
        // Purchased decremented by 1 (5 → 4)
        assertThat(user.getPurchasedFreezeBalance()).isEqualTo(4);
        // Save called because purchasedSpent > 0
        verify(userRepo).save(user);
        // Streak intact
        assertThat(user.getStreak()).isEqualTo(7);
    }

    /**
     * TEST 4: Shortfall of 3, only 1 bonus freeze + 0 purchased freezes available.
     * 2 days of shortfall remain uncovered → streak resets to 0.
     */
    @Test
    void testShortfallExceedsAvailable_streakBreaks() {
        UUID userId = UUID.randomUUID();
        User user = makeUser(userId, 15, 5, 0, 30);
        BonusFreezeGrant grant1 = makeActiveGrant(1L, 5);

        when(userRepo.findAllByStreakGreaterThan(0)).thenReturn(List.of(user));
        when(sessionRepo.countDistinctCompletedDaysInRange(
                eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(2L); // shortfall = 3
        when(bonusFreezeGrantService.getActiveGrants(userId)).thenReturn(List.of(grant1));

        scheduler.evaluateWeeklyStreaks();

        // The one bonus grant is still consumed before the break is declared
        verify(bonusFreezeGrantService).consumeGrant(grant1, "AUTO_APPLY");
        // Streak must be 0
        assertThat(user.getStreak()).isEqualTo(0);
        // Save called to persist streak = 0
        verify(userRepo).save(user);
    }

    /**
     * TEST 5: Account is only 3 days old — within the 7-day grace period.
     * The scheduler must skip the user entirely: no session query, no save,
     * streak stays unchanged.
     */
    @Test
    void testGracePeriod_newUserSkipped() {
        UUID userId = UUID.randomUUID();
        User user = makeUser(userId, 5, 5, 0, 3); // 3-day-old account

        when(userRepo.findAllByStreakGreaterThan(0)).thenReturn(List.of(user));

        scheduler.evaluateWeeklyStreaks();

        // Session repo must not be queried — user is skipped in grace check
        verify(sessionRepo, never())
                .countDistinctCompletedDaysInRange(any(), any(), any());
        verify(userRepo, never()).save(any());
        assertThat(user.getStreak()).isEqualTo(5);
    }

    /**
     * TEST 6: Two active bonus grants with different expiry dates.
     * Shortfall of 1 — only the soonest-expiring grant should be consumed.
     */
    @Test
    void testMultipleBonusGrants_soonestExpiringFirst() {
        UUID userId = UUID.randomUUID();
        User user = makeUser(userId, 8, 5, 0, 30);
        BonusFreezeGrant grant1 = makeActiveGrant(1L, 5);   // sooner expiry
        BonusFreezeGrant grant2 = makeActiveGrant(2L, 25);  // later expiry

        when(userRepo.findAllByStreakGreaterThan(0)).thenReturn(List.of(user));
        when(sessionRepo.countDistinctCompletedDaysInRange(
                eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(4L); // shortfall = 1
        // Service returns grants ASC by expiresAt — soonest first
        when(bonusFreezeGrantService.getActiveGrants(userId)).thenReturn(List.of(grant1, grant2));

        scheduler.evaluateWeeklyStreaks();

        // Only the sooner-expiring grant is consumed
        verify(bonusFreezeGrantService).consumeGrant(grant1, "AUTO_APPLY");
        // Later grant left untouched
        verify(bonusFreezeGrantService, never()).consumeGrant(eq(grant2), any());
        // Streak survives
        assertThat(user.getStreak()).isEqualTo(8);
    }
}
