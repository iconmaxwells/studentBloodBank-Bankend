package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.BloodUnit;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BloodUnitRepository extends JpaRepository<BloodUnit, String> {
    List<BloodUnit> findByBloodGroupAndBloodProductTypeAndStatusOrderByExpiryDateAsc(
            BloodGroup bloodGroup, BloodProductType bloodProductType, UnitStatus status);
    Page<BloodUnit> findByStatus(UnitStatus status, Pageable pageable);
    List<BloodUnit> findByExpiryDateBetweenAndStatus(LocalDate start, LocalDate end, UnitStatus status);

    Optional<BloodUnit> findFirstByDonorIdOrderByCreatedAtDesc(UUID donorId);

    @Query("SELECT u.bloodGroup, u.bloodProductType, COUNT(u) FROM BloodUnit u WHERE u.status = :status GROUP BY u.bloodGroup, u.bloodProductType")
    List<Object[]> summarizeByGroupAndType(@Param("status") UnitStatus status);
}
