package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HospitalRepository extends JpaRepository<Hospital, UUID> {
    Optional<Hospital> findByDisplayCode(String displayCode);
    Optional<Hospital> findByUserId(UUID userId);
    boolean existsByRegistrationNumber(String registrationNumber);

    @Query("SELECT h FROM Hospital h WHERE (:pattern IS NULL OR LOWER(h.name) LIKE :pattern " +
            "OR LOWER(h.displayCode) LIKE :pattern) AND (:status IS NULL OR h.status = :status)")
    Page<Hospital> search(@Param("pattern") String pattern, @Param("status") HospitalStatus status, Pageable pageable);
}
