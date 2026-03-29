package com.fittribe.api.repository;

import com.fittribe.api.entity.FeedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedItemRepository extends JpaRepository<FeedItem, UUID> {

    List<FeedItem> findTop30ByGroupIdOrderByCreatedAtDesc(UUID groupId);
}
