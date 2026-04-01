package com.fittribe.api.repository;

import com.fittribe.api.entity.DailyPlanGenerated;
import com.fittribe.api.entity.DailyPlanGeneratedId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyPlanGeneratedRepository extends JpaRepository<DailyPlanGenerated, DailyPlanGeneratedId> {

    Optional<DailyPlanGenerated> findByIdUserIdAndIdDate(UUID userId, LocalDate date);
}
