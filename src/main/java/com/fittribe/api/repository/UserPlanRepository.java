package com.fittribe.api.repository;

import com.fittribe.api.entity.UserPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPlanRepository extends JpaRepository<UserPlan, UUID> {

    Optional<UserPlan> findByUserIdAndWeekStartDate(UUID userId, LocalDate weekStartDate);

    List<UserPlan> findByUserIdOrderByWeekStartDateDesc(UUID userId);

    Optional<UserPlan> findFirstByUserIdOrderByWeekStartDateDesc(UUID userId);
}
