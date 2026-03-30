package com.fittribe.api.repository;

import com.fittribe.api.entity.SessionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, UUID> {

    Optional<SessionFeedback> findBySessionId(UUID sessionId);

    List<SessionFeedback> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SessionFeedback> findByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
