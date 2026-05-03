// WaitlistRepository.java
package com.fittribe.api.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Shifts positions down by 1 for all entries in [newPos, oldPos) to make room
     * for a referrer jumping up. Must run before setting the referrer's new position
     * to avoid violating the unique constraint on position.
     */
    @Modifying
    @Query("UPDATE WaitlistEntry w SET w.position = w.position + 1 " +
           "WHERE w.position >= :newPos AND w.position < :oldPos")
    int shiftPositionsDown(@Param("newPos") int newPos, @Param("oldPos") int oldPos);
}