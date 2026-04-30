package com.fittribe.api.service;

import com.fittribe.api.dto.response.BonusGrantSummary;
import com.fittribe.api.dto.response.FreezesSummary;
import com.fittribe.api.dto.response.HistoryEvent;
import com.fittribe.api.dto.response.HistoryResponse;
import com.fittribe.api.dto.response.StreakStateResponse;
import com.fittribe.api.dto.response.ThisWeekSummary;
import com.fittribe.api.entity.BonusFreezeGrant;
import com.fittribe.api.entity.FreezeTransaction;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.FreezeTransactionRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.util.Zones;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
public class StreakStateService {

    private static final int HISTORY_DAYS     = 90;
    private static final int HISTORY_MAX_ROWS = 200;

    private final UserRepository userRepository;
    private final WorkoutSessionRepository workoutSessionRepo;
    private final BonusFreezeGrantService bonusFreezeGrantService;
    private final FreezeTransactionRepository freezeTransactionRepo;

    public StreakStateService(UserRepository userRepository,
                              WorkoutSessionRepository workoutSessionRepo,
                              BonusFreezeGrantService bonusFreezeGrantService,
                              FreezeTransactionRepository freezeTransactionRepo) {
        this.userRepository          = userRepository;
        this.workoutSessionRepo      = workoutSessionRepo;
        this.bonusFreezeGrantService = bonusFreezeGrantService;
        this.freezeTransactionRepo   = freezeTransactionRepo;
    }

    public StreakStateResponse getState(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        // ── Week bounds (IST) ────────────────────────────────────────────
        LocalDate today      = Zones.fitnessDayNow();
        LocalDate weekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekStart    = weekMonday.atStartOfDay(Zones.APP_ZONE).toInstant();
        Instant weekEnd      = weekMonday.plusWeeks(1).atStartOfDay(Zones.APP_ZONE).toInstant();

        // ── Days remaining in the current ISO week ───────────────────────
        // ISO: Monday=1 … Sunday=7; 7 - getValue() gives days left in the week (0 on Sunday, 6 on Monday)
        int daysRemainingInWeek = 7 - today.getDayOfWeek().getValue();

        // ── Sessions completed this week ─────────────────────────────────
        int completed = (int) workoutSessionRepo.countDistinctCompletedDaysInRange(
                userId, weekStart, weekEnd);

        // ── Weekly goal ──────────────────────────────────────────────────
        int weeklyGoal = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;
        int shortfall  = Math.max(0, weeklyGoal - completed);

        // ── Active bonus grants ──────────────────────────────────────────
        List<BonusFreezeGrant> activeGrants =
                bonusFreezeGrantService.getActiveGrants(userId);

        List<BonusGrantSummary> bonusGrantSummaries = activeGrants.stream()
                .map(g -> new BonusGrantSummary(g.getId(), g.getExpiresAt()))
                .toList();

        int activeBonusCount = activeGrants.size();
        int purchasedBalance = user.getPurchasedFreezeBalance() != null
                ? user.getPurchasedFreezeBalance() : 0;
        int totalAvailable   = purchasedBalance + activeBonusCount;

        // ── Projected status ─────────────────────────────────────────────
        String projectedStatus;
        if (shortfall == 0) {
            projectedStatus = "SAFE";
        } else if (totalAvailable >= shortfall) {
            projectedStatus = "AT_RISK";
        } else {
            projectedStatus = "WILL_BREAK";
        }

        // ── Assemble response ────────────────────────────────────────────
        ThisWeekSummary thisWeek = new ThisWeekSummary(
                weekMonday,
                completed,
                weeklyGoal,
                shortfall,
                daysRemainingInWeek);

        FreezesSummary freezes = new FreezesSummary(
                purchasedBalance,
                activeBonusCount,
                totalAvailable,
                bonusGrantSummaries);

        boolean autoFreezeEnabled = !Boolean.FALSE.equals(user.getAutoFreezeEnabled());

        return new StreakStateResponse(
                user.getStreak() != null ? user.getStreak() : 0,
                user.getMaxStreakEver() != null ? user.getMaxStreakEver() : 0,
                thisWeek,
                freezes,
                projectedStatus,
                autoFreezeEnabled);
    }

    public HistoryResponse getHistory(UUID userId, Integer days) {
        userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        int resolvedDays = (days == null || days <= 0) ? HISTORY_DAYS : Math.min(days, HISTORY_DAYS);
        // resolvedDays-day window + 200-row cap; returnedCount reflects rows returned, not total in DB
        Instant since = Instant.now().minus(resolvedDays, ChronoUnit.DAYS);
        Pageable page = PageRequest.of(0, HISTORY_MAX_ROWS);
        List<FreezeTransaction> txs = freezeTransactionRepo.findRecentForUser(userId, since, page);
        List<HistoryEvent> events = txs.stream()
                .map(t -> new HistoryEvent(t.getEventType(), t.getAmount(), t.getOccurredAt(), t.getMetadata()))
                .toList();
        return new HistoryResponse(events, events.size());
    }
}
