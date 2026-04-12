package com.fittribe.api.bonus;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for bonus_session_generated table.
 * (user_id, date, bonus_number) uniquely identifies a bonus generation.
 */
@Embeddable
public class BonusSessionGeneratedId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "bonus_number")
    private Integer bonusNumber;

    public BonusSessionGeneratedId() {}

    public BonusSessionGeneratedId(UUID userId, LocalDate date, Integer bonusNumber) {
        this.userId = userId;
        this.date = date;
        this.bonusNumber = bonusNumber;
    }

    public UUID      getUserId()      { return userId; }
    public void      setUserId(UUID v)      { this.userId = v; }
    public LocalDate getDate()        { return date; }
    public void      setDate(LocalDate v)   { this.date = v; }
    public Integer   getBonusNumber() { return bonusNumber; }
    public void      setBonusNumber(Integer v) { this.bonusNumber = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BonusSessionGeneratedId that)) return false;
        return Objects.equals(userId, that.userId)
            && Objects.equals(date, that.date)
            && Objects.equals(bonusNumber, that.bonusNumber);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, date, bonusNumber); }
}
