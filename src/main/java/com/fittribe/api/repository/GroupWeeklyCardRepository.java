package com.fittribe.api.repository;

import com.fittribe.api.entity.GroupWeeklyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupWeeklyCardRepository extends JpaRepository<GroupWeeklyCard, UUID> {

    Optional<GroupWeeklyCard> findByGroupIdAndIsoYearAndIsoWeek(UUID groupId, int isoYear, int isoWeek);

    List<GroupWeeklyCard> findTop10ByGroupIdOrderByLockedAtDesc(UUID groupId);

    List<GroupWeeklyCard> findByGroupIdOrderByIsoYearDescIsoWeekDesc(UUID groupId);
}
