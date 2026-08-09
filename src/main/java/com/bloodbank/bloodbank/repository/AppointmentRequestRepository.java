package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.AppointmentRequest;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, UUID> {
    Page<AppointmentRequest> findByStatus(AppointmentRequestStatus status, Pageable pageable);
}
