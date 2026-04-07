package com.fittribe.api.repository;

import com.fittribe.api.entity.SavedRoutine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedRoutineRepository extends JpaRepository<SavedRoutine, UUID> {

    @Query("SELECT r FROM SavedRoutine r WHERE r.userId = :userId ORDER BY r.lastUsedAt DESC NULLS LAST, r.createdAt DESC")
    List<SavedRoutine> findAllByUserSortedByRecent(@Param("userId") UUID userId);

    Optional<SavedRoutine> findByIdAndUserId(UUID id, UUID userId);
}
