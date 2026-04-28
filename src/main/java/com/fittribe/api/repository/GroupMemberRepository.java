package com.fittribe.api.repository;

import com.fittribe.api.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findByGroupId(UUID groupId);

    List<GroupMember> findByUserId(UUID userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    /** Batch load: all memberships for a set of groups in one query. */
    List<GroupMember> findByGroupIdIn(Collection<UUID> groupIds);

    /** Returns only the user IDs for a group — avoids loading full GroupMember entities for leaderboard fan-out. */
    @Query("SELECT gm.userId FROM GroupMember gm WHERE gm.groupId = :groupId")
    List<UUID> findUserIdsByGroupId(@Param("groupId") UUID groupId);
}
