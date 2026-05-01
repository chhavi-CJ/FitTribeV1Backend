package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.entity.UserDayStatusId;
import com.fittribe.api.fitnesssummary.FitnessSummaryService;
import com.fittribe.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceSetStatusTest {

    @Mock UserRepository               userRepo;
    @Mock UserPlanRepository           planRepo;
    @Mock ExerciseRepository           exerciseRepo;
    @Mock WorkoutSessionRepository     sessionRepo;
    @Mock SetLogRepository             setLogRepo;
    @Mock AiInsightRepository          insightRepo;
    @Mock SessionFeedbackRepository    feedbackRepo;
    @Mock SplitTemplateDayRepository   splitTemplateDayRepo;
    @Mock DailyPlanGeneratedRepository dailyPlanRepo;
    @Mock UserDayStatusRepository      dayStatusRepo;
    @Mock FitnessSummaryService        fitnessSummaryService;
    @Mock PlanHistoryService           planHistoryService;
    @Mock FeedEventWriter              feedEventWriter;

    PlanService planService;

    final UUID      userId = UUID.randomUUID();
    final LocalDate today  = LocalDate.now();

    @BeforeEach
    void setUp() {
        planService = new PlanService(
                userRepo, planRepo, exerciseRepo, sessionRepo, setLogRepo,
                insightRepo, feedbackRepo, splitTemplateDayRepo, dailyPlanRepo,
                dayStatusRepo, fitnessSummaryService, planHistoryService,
                new ObjectMapper(), feedEventWriter);
    }

    // ── READY ────────────────────────────────────────────────────────────

    @Test
    void ready_returns_200_with_correct_keys() {
        when(dayStatusRepo.findByIdUserIdAndIdDate(userId, today)).thenReturn(Optional.empty());

        Map<String, Object> result = planService.setTodayStatus(userId, "READY", today);

        assertThat(result.get("status")).isEqualTo("READY");
        assertThat(result.get("message")).isNotNull().isNotEqualTo("");
    }

    @Test
    void ready_deletes_existing_status_row() {
        UserDayStatus existing = new UserDayStatus(userId, today, "REST");
        when(dayStatusRepo.findByIdUserIdAndIdDate(userId, today)).thenReturn(Optional.of(existing));

        planService.setTodayStatus(userId, "READY", today);

        verify(dayStatusRepo).delete(existing);
    }

    @Test
    void ready_with_no_existing_row_does_not_call_delete() {
        when(dayStatusRepo.findByIdUserIdAndIdDate(userId, today)).thenReturn(Optional.empty());

        planService.setTodayStatus(userId, "READY", today);

        verify(dayStatusRepo, never()).delete(any(UserDayStatus.class));
    }

    @Test
    void ready_does_not_query_session_repo() {
        when(dayStatusRepo.findByIdUserIdAndIdDate(userId, today)).thenReturn(Optional.empty());

        planService.setTodayStatus(userId, "READY", today);

        // READY exits before any session guard — session repo must not be touched
        verifyNoInteractions(sessionRepo);
    }

    @Test
    void ready_does_not_write_a_new_status_row() {
        when(dayStatusRepo.findByIdUserIdAndIdDate(userId, today)).thenReturn(Optional.empty());

        planService.setTodayStatus(userId, "READY", today);

        verify(dayStatusRepo, never()).save(any());
    }

    // ── Existing statuses still work ─────────────────────────────────────

    @Test
    void rest_follows_normal_path_and_saves_row() {
        when(sessionRepo.findFirstByUserIdAndStatusAndFinishedAtAfter(any(), eq("IN_PROGRESS"), any()))
                .thenReturn(Optional.empty());
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(any(), eq("COMPLETED"), any(), any()))
                .thenReturn(false);
        when(dayStatusRepo.findByIdUserIdAndIdDate(userId, today)).thenReturn(Optional.empty());

        Map<String, Object> result = planService.setTodayStatus(userId, "REST", today);

        assertThat(result.get("status")).isEqualTo("REST");
        verify(dayStatusRepo).save(any(UserDayStatus.class));
    }
}
