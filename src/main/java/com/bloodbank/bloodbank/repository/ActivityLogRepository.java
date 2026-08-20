package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.ActivityLog;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID>, JpaSpecificationExecutor<ActivityLog> {
    Page<ActivityLog> findByActionType(ActionType actionType, Pageable pageable);
    Page<ActivityLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
