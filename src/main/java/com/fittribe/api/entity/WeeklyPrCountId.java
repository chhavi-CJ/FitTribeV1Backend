package com.fittribe.api.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link WeeklyPrCount}.
 */
public class WeeklyPrCountId implements Serializable {
    public UUID userId;
    public LocalDate weekStart;

    public WeeklyPrCountId() {}

    public WeeklyPrCountId(UUID userId, LocalDate weekStart) {
        this.userId = userId;
        this.weekStart = weekStart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeeklyPrCountId that = (WeeklyPrCountId) o;
        return Objects.equals(userId, that.userId) &&
               Objects.equals(weekStart, that.weekStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, weekStart);
    }
}
