package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DonorRepository extends JpaRepository<Donor, UUID> {
    Optional<Donor> findByDisplayCode(String displayCode);
    Optional<Donor> findByUserId(UUID userId);
    boolean existsByIdNumber(String idNumber);
    boolean existsByUser_Email(String email);

    @Query("SELECT d FROM Donor d WHERE (:pattern IS NULL OR LOWER(d.firstName) LIKE :pattern " +
            "OR LOWER(d.lastName) LIKE :pattern OR LOWER(d.displayCode) LIKE :pattern) " +
            "AND (:status IS NULL OR d.status = :status)")
    Page<Donor> search(@Param("pattern") String pattern, @Param("status") DonorStatus status, Pageable pageable);
}
