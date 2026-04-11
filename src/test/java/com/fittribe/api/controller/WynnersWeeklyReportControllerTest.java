package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.response.WeeklyReportDto;
import com.fittribe.api.entity.WeeklyReport;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.WeeklyReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WynnersWeeklyReportController}.
 *
 * <p>Repository is a JPA interface — Mockito handles those fine on JDK 25.
 * {@link ObjectMapper} is constructed directly (no mocking) with the Java-
 * time module registered, matching the behaviour of the Spring Boot
 * auto-configured bean that the production controller receives.
 *
 * <p>{@link Authentication} is constructed as a real
 * {@link UsernamePasswordAuthenticationToken}, matching exactly what
 * {@link com.fittribe.api.filter.JwtAuthFilter} puts into the
 * {@code SecurityContext} after validating a JWT.
 *
 * <p>Exception assertions use {@code assertThrows(ApiException.class, ...)}
 * and check {@code getStatus()} / {@code getCode()} directly — no MVC
 * needed because {@code GlobalExceptionHandler} converts them to HTTP
 * responses, but that path is covered by its own suite.
 */
class WynnersWeeklyReportControllerTest {

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private WeeklyReportRepository repo;
    private WynnersWeeklyReportController controller;

    @BeforeEach
    void setUp() {
        repo = mock(WeeklyReportRepository.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        controller = new WynnersWeeklyReportController(repo, mapper);
    }

    // ── GET /latest ───────────────────────────────────────────────────────

    @Test
    void latestReturns200WhenReportExists() {
        WeeklyReport row = buildRow(99L, USER_A, 3);
        when(repo.findTopByUserIdOrderByWeekStartDesc(USER_A)).thenReturn(Optional.of(row));

        ResponseEntity<ApiResponse<WeeklyReportDto>> response = controller.latest(auth(USER_A));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        WeeklyReportDto dto = response.getBody().getData();
        assertNotNull(dto);
        assertEquals(99L, dto.getId());
        assertEquals(3, dto.getWeekNumber());
        assertEquals(4, dto.getSessionsLogged());
        assertEquals("Arjun", dto.getUserFirstName());
        // JSONB columns were "[]" in fixture — must be empty lists, not null
        assertNotNull(dto.getFindings());
        assertNotNull(dto.getMuscleCoverage());
    }

    @Test
    void latestReturns404WhenNoReport() {
        when(repo.findTopByUserIdOrderByWeekStartDesc(USER_A)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.latest(auth(USER_A)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("NOT_FOUND", ex.getCode());
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────

    @Test
    void byIdReturns200WhenOwner() {
        WeeklyReport row = buildRow(7L, USER_A, 1);
        when(repo.findById(7L)).thenReturn(Optional.of(row));

        ResponseEntity<ApiResponse<WeeklyReportDto>> response = controller.byId(7L, auth(USER_A));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        WeeklyReportDto dto = response.getBody().getData();
        assertNotNull(dto);
        assertEquals(7L, dto.getId());
        assertEquals(1, dto.getWeekNumber());
    }

    @Test
    void byIdReturns403WhenDifferentUser() {
        // Row belongs to USER_A; authenticated caller is USER_B
        WeeklyReport row = buildRow(7L, USER_A, 1);
        when(repo.findById(7L)).thenReturn(Optional.of(row));

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.byId(7L, auth(USER_B)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void byIdReturns404WhenNotFound() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> controller.byId(999L, auth(USER_A)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("NOT_FOUND", ex.getCode());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Build a minimal {@link WeeklyReport} with all non-null columns set.
     * JSONB columns are {@code "[]"} so the DTO factory doesn't throw on
     * deserialization — the DTO unit tests cover richer JSONB shapes.
     */
    private static WeeklyReport buildRow(Long id, UUID userId, int weekNumber) {
        WeeklyReport row = new WeeklyReport();
        row.setId(id);
        row.setUserId(userId);
        row.setWeekNumber(weekNumber);
        row.setWeekStart(LocalDate.of(2026, 4, 6));
        row.setWeekEnd(LocalDate.of(2026, 4, 13));
        row.setUserFirstName("Arjun");
        row.setIsWeekOne(false);
        row.setSessionsLogged(4);
        row.setSessionsGoal(4);
        row.setTotalKgVolume(new BigDecimal("8400.00"));
        row.setPrCount(1);
        row.setVerdict("Consistent week — full goal hit.");
        row.setPersonalRecords("[]");
        row.setBaselines("[]");
        row.setMuscleCoverage("[]");
        row.setFindings("[]");
        row.setRecalibrations("[]");
        row.setSchemaVersion(1);
        return row;
    }

    /**
     * Create an {@link Authentication} that mirrors what
     * {@link com.fittribe.api.filter.JwtAuthFilter} puts into the
     * {@code SecurityContext} — a {@link UsernamePasswordAuthenticationToken}
     * whose principal is the user's {@link UUID}.
     */
    private static Authentication auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
