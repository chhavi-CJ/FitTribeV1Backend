package com.fittribe.api.repository;

import com.fittribe.api.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByPhone(String phone);

    /**
     * Fetches the user row with a PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE).
     *
     * Must be used in any transaction that reads coins before deducting them.
     * Prevents two concurrent requests from both passing the balance check
     * and both deducting, which would leave the balance negative.
     *
     * Usage pattern:
     *   User user = userRepo.findByIdForUpdate(userId)
     *                       .orElseThrow(() -> ApiException.notFound("User"));
     *   if (user.getCoins() < cost) throw ApiException.insufficientCoins();
     *   user.setCoins(user.getCoins() - cost);
     *   userRepo.save(user);
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /** Batch load: fetch multiple users by ID in one query. */
    List<User> findByIdIn(Collection<UUID> ids);

    /** All users with an active streak — used by the daily streak reset job. */
    List<User> findAllByStreakGreaterThan(int streak);

    /** All users with a pending weekly goal — used by the Monday promotion scheduler. */
    List<User> findAllByPendingWeeklyGoalIsNotNull();
}
