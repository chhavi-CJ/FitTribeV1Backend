package com.fittribe.api.repository;

import com.fittribe.api.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    List<DeviceToken> findByUserId(UUID userId);

    @Transactional
    void deleteByUserIdAndToken(UUID userId, String token);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    void deleteAllByToken(String token);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO device_tokens (user_id, token, platform)
            VALUES (:userId, :token, :platform)
            ON CONFLICT (user_id, token) DO UPDATE SET
              platform     = EXCLUDED.platform,
              last_seen_at = NOW()
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId,
                @Param("token") String token,
                @Param("platform") String platform);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = """
            DELETE FROM device_tokens
            WHERE user_id = :userId AND token != :token
            """, nativeQuery = true)
    void deleteAllOtherTokensForUser(@Param("userId") UUID userId,
                                     @Param("token") String token);

    @Query("SELECT COUNT(d) FROM DeviceToken d WHERE d.platform = :platform")
    long countByPlatform(@Param("platform") String platform);

    @Query("SELECT COUNT(DISTINCT d.userId) FROM DeviceToken d")
    long countDistinctUserId();
}
