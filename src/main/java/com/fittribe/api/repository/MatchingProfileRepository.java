package com.fittribe.api.repository;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchingProfileRepository extends JpaRepository<MatchingProfile, UUID> {

    Optional<MatchingProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    List<MatchingProfile> findByArchetype(Archetype archetype);
}
