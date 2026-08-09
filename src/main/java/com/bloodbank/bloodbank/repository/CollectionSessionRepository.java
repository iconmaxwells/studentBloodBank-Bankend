package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.CollectionSession;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CollectionSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollectionSessionRepository extends JpaRepository<CollectionSession, UUID> {
    Optional<CollectionSession> findByStaffIdAndStatus(UUID staffId, CollectionSessionStatus status);
    Page<CollectionSession> findByStaffId(UUID staffId, Pageable pageable);
}
