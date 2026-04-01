package com.fittribe.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_day_status")
public class UserDayStatus {

    @EmbeddedId
    private UserDayStatusId id;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public UserDayStatus() {}

    public UserDayStatus(UUID userId, LocalDate date, String status) {
        this.id = new UserDayStatusId(userId, date);
        this.status = status;
    }

    public UserDayStatusId getId()           { return id; }
    public String getStatus()                { return status; }
    public void setStatus(String status)     { this.status = status; }
    public Instant getCreatedAt()            { return createdAt; }
}
