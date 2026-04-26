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
        Integer streak,
        List<String> muscleGroups,
        int firstEverCount,
        int prCount,
        List<ExerciseGroup> exercises,
        FeedbackInfo feedback
) {
    public record ExerciseGroup(
            String name,
            String muscleGroup,
            boolean firstEver,
            List<SetSummary> sets) {}

    public record SetSummary(
            UUID id,
            BigDecimal kg,
            int reps,
            boolean isPr) {}
}
