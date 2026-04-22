package com.fittribe.api.repository;

import com.fittribe.api.entity.SessionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, UUID> {

    Optional<SessionFeedback> findBySessionId(UUID sessionId);

    List<SessionFeedback> findBySessionIdIn(Collection<UUID> sessionIds);

    List<SessionFeedback> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SessionFeedback> findByUserIdAndCreatedAtAfter(UUID userId, Instant after);

    /** Feedback entries for a user in a half-open window [from, to). Used by FitnessSummaryService. */
    List<SessionFeedback> findByUserIdAndCreatedAtBetween(UUID userId, Instant from, Instant to);
}
