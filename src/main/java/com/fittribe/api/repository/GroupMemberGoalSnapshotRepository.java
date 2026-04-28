package com.fittribe.api.repository;

import com.fittribe.api.entity.GroupMemberGoalSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberGoalSnapshotRepository extends JpaRepository<GroupMemberGoalSnapshot, UUID> {

    List<GroupMemberGoalSnapshot> findByGroupIdAndIsoYearAndIsoWeek(UUID groupId, int isoYear, int isoWeek);

    Optional<GroupMemberGoalSnapshot> findByGroupIdAndUserIdAndIsoYearAndIsoWeek(
            UUID groupId, UUID userId, int isoYear, int isoWeek);

    List<GroupMemberGoalSnapshot> findByUserIdAndIsoYearAndIsoWeek(UUID userId, int isoYear, int isoWeek);
}
