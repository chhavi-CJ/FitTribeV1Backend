package com.fittribe.api.repository;

import com.fittribe.api.entity.GroupWeeklyTopPerformer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupWeeklyTopPerformerRepository extends JpaRepository<GroupWeeklyTopPerformer, UUID> {

    Optional<GroupWeeklyTopPerformer> findByGroupIdAndIsoYearAndIsoWeekAndDimension(
            UUID groupId, int isoYear, int isoWeek, String dimension);

    /** Most recent locked top performer for a group+dimension across all weeks. */
    Optional<GroupWeeklyTopPerformer> findTopByGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(
            UUID groupId, String dimension);

    /** Full win history for a user in a group+dimension, newest first. Used for rotation rule. */
    List<GroupWeeklyTopPerformer> findByWinnerUserIdAndGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(
            UUID winnerUserId, UUID groupId, String dimension);
}
