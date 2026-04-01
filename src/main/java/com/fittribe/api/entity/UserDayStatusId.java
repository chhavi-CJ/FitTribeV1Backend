package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserDayStatusId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "date")
    private LocalDate date;

    public UserDayStatusId() {}

    public UserDayStatusId(UUID userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
    }

    public UUID getUserId()      { return userId; }
    public LocalDate getDate()   { return date; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserDayStatusId)) return false;
        UserDayStatusId that = (UserDayStatusId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, date);
    }
}
