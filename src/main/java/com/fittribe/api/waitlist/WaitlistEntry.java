package com.fittribe.api.waitlist;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(name = "referral_code", nullable = false, unique = true)
    private String referralCode;

    @Column(name = "referred_by_code")
    private String referredByCode;

    @Column(name = "position", nullable = false, insertable = false)
    // insertable = false: Postgres sequence assigns initial value via DEFAULT.
    // No updatable = false because applyReferralJump() legitimately mutates position.
    private int position;

    // Both flags false: set once by waitlist_entries_set_start_position trigger; never changed.
    @Column(name = "start_position", nullable = false, insertable = false, updatable = false)
    private int startPosition;

    @Column(name = "referral_count", nullable = false)
    private int referralCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public String getReferredByCode() { return referredByCode; }
    public void setReferredByCode(String referredByCode) { this.referredByCode = referredByCode; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public int getStartPosition() { return startPosition; }
    public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
    public int getReferralCount() { return referralCount; }
    public void setReferralCount(int referralCount) { this.referralCount = referralCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
