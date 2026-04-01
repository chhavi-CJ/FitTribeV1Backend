package com.fittribe.api.repository;

import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.entity.UserDayStatusId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDayStatusRepository extends JpaRepository<UserDayStatus, UserDayStatusId> {

    Optional<UserDayStatus> findByIdUserIdAndIdDate(UUID userId, LocalDate date);
}
