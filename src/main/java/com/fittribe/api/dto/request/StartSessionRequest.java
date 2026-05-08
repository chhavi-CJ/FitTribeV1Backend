package com.fittribe.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Body for {@code POST /api/v1/sessions/start}.
 *
 * <p>{@code clientId} is optional: when present, it becomes the
 * canonical {@code workout_sessions.id}, enabling idempotent retries
 * and client-side optimistic UI. When absent (legacy frontends), the
 * server generates the id.
 */
public record StartSessionRequest(
        @NotBlank String name,
        String badge,
        String source,
        UUID sourceRoutineId,
        List<Map<String, Object>> plannedExercises,
        UUID clientId
) {}
