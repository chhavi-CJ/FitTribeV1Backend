package com.fittribe.api.repository;

import com.fittribe.api.entity.GroupWeeklyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupWeeklyProgressRepository extends JpaRepository<GroupWeeklyProgress, UUID> {

    Optional<GroupWeeklyProgress> findByGroupIdAndIsoYearAndIsoWeek(UUID groupId, int isoYear, int isoWeek);

    /** Used by the Sunday lock job to find all unlocked progress rows for a given week. */
    List<GroupWeeklyProgress> findByIsoYearAndIsoWeekAndLockedAtIsNull(int isoYear, int isoWeek);
}
