package com.fittribe.api.repository;

import com.fittribe.api.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    List<Reaction> findByFeedItemIdIn(Collection<UUID> feedItemIds);

    Optional<Reaction> findByFeedItemIdAndUserId(UUID feedItemId, UUID userId);

    @Transactional
    void deleteByFeedItemIdAndUserId(UUID feedItemId, UUID userId);
}
