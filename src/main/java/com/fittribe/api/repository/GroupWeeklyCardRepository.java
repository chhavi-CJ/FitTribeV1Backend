package com.fittribe.api.repository;

import com.fittribe.api.entity.GroupWeeklyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupWeeklyCardRepository extends JpaRepository<GroupWeeklyCard, UUID> {

    Optional<GroupWeeklyCard> findByGroupIdAndIsoYearAndIsoWeek(UUID groupId, int isoYear, int isoWeek);

    List<GroupWeeklyCard> findTop10ByGroupIdOrderByLockedAtDesc(UUID groupId);

    List<GroupWeeklyCard> findByGroupIdOrderByIsoYearDescIsoWeekDesc(UUID groupId);

    /**
     * Count the leading run of GOLD cards (most recent first) for a group.
     * Returns 0 if the most recent card is non-GOLD or no cards exist.
     * Uses idx_gwc_group_week — zero row transfer, server-side count.
     */
    @Query(value = """
            WITH ranked AS (
              SELECT final_tier,
                     ROW_NUMBER() OVER (ORDER BY iso_year DESC, iso_week DESC) AS rn
              FROM group_weekly_cards
              WHERE group_id = :groupId
            )
            SELECT COUNT(*)::int
            FROM ranked
            WHERE final_tier = 'GOLD'
              AND rn < COALESCE((SELECT MIN(rn) FROM ranked WHERE final_tier <> 'GOLD'), 2147483647)
            """, nativeQuery = true)
    int countLeadingGoldStreak(@Param("groupId") UUID groupId);
}
