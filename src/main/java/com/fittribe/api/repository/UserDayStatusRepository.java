package com.fittribe.api.repository;

import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.entity.UserDayStatusId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDayStatusRepository extends JpaRepository<UserDayStatus, UserDayStatusId> {

    Optional<UserDayStatus> findByIdUserIdAndIdDate(UUID userId, LocalDate date);

    /** Batch variant — all status rows for a set of users on a given date. */
    List<UserDayStatus> findByIdUserIdInAndIdDate(Collection<UUID> userIds, LocalDate date);
}
