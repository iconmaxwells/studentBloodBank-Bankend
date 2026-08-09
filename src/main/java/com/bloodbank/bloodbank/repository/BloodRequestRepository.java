package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.BloodRequest;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, UUID> {
    Optional<BloodRequest> findByDisplayCode(String displayCode);
    Page<BloodRequest> findByHospitalId(UUID hospitalId, Pageable pageable);
    Page<BloodRequest> findByStatus(RequestStatus status, Pageable pageable);
    long countByHospitalIdAndStatus(UUID hospitalId, RequestStatus status);
}
