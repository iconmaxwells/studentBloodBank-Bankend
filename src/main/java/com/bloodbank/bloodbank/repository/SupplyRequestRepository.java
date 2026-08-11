package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.SupplyRequest;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.SupplyRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SupplyRequestRepository extends JpaRepository<SupplyRequest, UUID> {
    Optional<SupplyRequest> findByDisplayCode(String displayCode);

    Page<SupplyRequest> findByStatus(SupplyRequestStatus status, Pageable pageable);

    long countByStatusIn(java.util.Collection<SupplyRequestStatus> statuses);
}
