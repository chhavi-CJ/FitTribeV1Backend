package com.fittribe.api.repository;

import com.fittribe.api.entity.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiInsightRepository extends JpaRepository<AiInsight, UUID> {

    List<AiInsight> findByUserIdOrderByGeneratedAtDesc(UUID userId);

    List<AiInsight> findByUserIdAndInsightType(UUID userId, String insightType);

    long countByUserIdAndInsightType(UUID userId, String insightType);

    // Used for weekly reports: exerciseName stores weekNumber as string
    Optional<AiInsight> findTopByUserIdAndInsightTypeAndExerciseNameOrderByGeneratedAtDesc(
            UUID userId, String insightType, String exerciseName);
}
