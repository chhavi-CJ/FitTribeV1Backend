package com.fittribe.api.repository;

import com.fittribe.api.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    List<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE notifications SET read_at = NOW() WHERE recipient_id = :recipientId AND read_at IS NULL",
           nativeQuery = true)
    void markAllReadByRecipientId(@Param("recipientId") UUID recipientId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO notifications
              (recipient_id, actor_id, type, feed_item_id, group_id, metadata)
            VALUES (:recipientId, :actorId, 'REACTION', :feedItemId, :groupId, CAST(:metadataJson AS jsonb))
            ON CONFLICT (recipient_id, actor_id, feed_item_id, type)
              WHERE type = 'REACTION'
            DO UPDATE SET
              metadata   = EXCLUDED.metadata,
              created_at = NOW(),
              read_at    = NULL
            """, nativeQuery = true)
    void upsertReactionNotification(@Param("recipientId") UUID recipientId,
                                    @Param("actorId") UUID actorId,
                                    @Param("feedItemId") UUID feedItemId,
                                    @Param("groupId") UUID groupId,
                                    @Param("metadataJson") String metadataJson);
}
