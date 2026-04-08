package com.fittribe.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SessionHistoryItem(
        UUID id,
        String name,
        String date,
        BigDecimal totalVolumeKg,
        int totalSets,
        Integer durationMins,
        List<ExerciseGroup> exercises,
        FeedbackInfo feedback
) {
    public record ExerciseGroup(String name, List<SetSummary> sets) {}

    public record SetSummary(BigDecimal kg, int reps) {}
}
