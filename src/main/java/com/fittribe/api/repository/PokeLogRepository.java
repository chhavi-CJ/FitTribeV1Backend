package com.fittribe.api.repository;

import com.fittribe.api.entity.PokeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PokeLogRepository extends JpaRepository<PokeLog, UUID> {

    /** Rate-limit check: has this recipient already been poked today in this group? */
    boolean existsByGroupIdAndRecipientUserIdAndPokedDate(
            UUID groupId, UUID recipientUserId, LocalDate pokedDate);

    /**
     * Batch load: all member IDs that were poked today in this group.
     * Used by GET /groups/{id}/members to populate the pokedToday field.
     */
    @Query("SELECT p.recipientUserId FROM PokeLog p " +
           "WHERE p.groupId = :groupId AND p.pokedDate = :date")
    Set<UUID> findRecipientsPokdToday(@Param("groupId") UUID groupId,
                                      @Param("date")    LocalDate date);
}
