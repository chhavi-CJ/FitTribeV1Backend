package com.fittribe.api.repository;

import com.fittribe.api.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByInviteCode(String inviteCode);

    List<Group> findByCreatedBy(UUID userId);

    /** Batch load: all groups for a set of IDs in one query. */
    List<Group> findByIdIn(Collection<UUID> ids);
}
