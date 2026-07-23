package com.fittribe.api.group;

import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.GrinderCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrinderCalculatorTest {

    private WorkoutSessionRepository sessionRepo;
    private GrinderCalculator        calculator;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sessionRepo = mock(WorkoutSessionRepository.class);
        calculator  = new GrinderCalculator(sessionRepo);
    }

    @Test
    void returns_correct_sessions_and_volume_for_active_user() {
        when(sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(Instant.class), any(Instant.class)))
                .thenReturn(12);
        when(sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(
                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("7800.00"));

        GrinderCalculator.GrinderResult r = calculator.compute(USER_ID);

        assertEquals(USER_ID, r.userId);
        assertEquals(12, r.totalSessions60Days);
        assertEquals(0, new BigDecimal("7800.00").compareTo(r.totalVolume60Days));
    }

    @Test
    void returns_zero_for_user_with_no_sessions() {
        when(sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(Instant.class), any(Instant.class)))
                .thenReturn(0);
        when(sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(
                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        GrinderCalculator.GrinderResult r = calculator.compute(USER_ID);

        assertEquals(0, r.totalSessions60Days);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.totalVolume60Days));
    }

    @Test
    void null_volume_from_repo_is_normalized_to_zero() {
        when(sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(Instant.class), any(Instant.class)))
                .thenReturn(3);
        when(sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(
                eq(USER_ID), any(Instant.class), any(Instant.class)))
                .thenReturn(null); // DB returns null when there are no matching rows

        GrinderCalculator.GrinderResult r = calculator.compute(USER_ID);

        assertEquals(3, r.totalSessions60Days);
        assertEquals(0, BigDecimal.ZERO.compareTo(r.totalVolume60Days));
    }
}
