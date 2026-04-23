// WaitlistRepository.java
package com.fittribe.api.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    Optional<WaitlistEntry> findByEmailIgnoreCase(String email);

    Optional<WaitlistEntry> findByPhone(String phone);

    Optional<WaitlistEntry> findByReferralCode(String referralCode);

    long count();

    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.referralCount = w.referralCount + 1 WHERE w.referralCode = :code")
    int incrementReferralCount(String code);
}