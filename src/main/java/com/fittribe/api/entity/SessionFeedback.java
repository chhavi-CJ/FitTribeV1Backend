package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_session_feedback")
public class SessionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(nullable = false)
    private String rating;

    @Column(length = 200)
    private String notes;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public SessionFeedback() {}

    public UUID getId()               { return id; }
    public void setId(UUID id)        { this.id = id; }

    public UUID getUserId()               { return userId; }
    public void setUserId(UUID userId)    { this.userId = userId; }

    public UUID getSessionId()                { return sessionId; }
    public void setSessionId(UUID sessionId)  { this.sessionId = sessionId; }

    public String getRating()              { return rating; }
    public void setRating(String rating)   { this.rating = rating; }

    public String getNotes()           { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt()      { return createdAt; }
}
