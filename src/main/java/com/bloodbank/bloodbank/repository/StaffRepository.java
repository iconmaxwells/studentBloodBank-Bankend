package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {
    Optional<Staff> findByUserId(UUID userId);
    Page<Staff> findByStatus(StaffStatus status, Pageable pageable);
}
