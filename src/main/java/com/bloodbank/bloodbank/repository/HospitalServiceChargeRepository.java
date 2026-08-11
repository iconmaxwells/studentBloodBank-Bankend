package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.HospitalServiceCharge;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HospitalServiceChargeRepository extends JpaRepository<HospitalServiceCharge, UUID> {
    boolean existsByHospitalIdAndBillingPeriod(UUID hospitalId, String billingPeriod);

    Optional<HospitalServiceCharge> findByHospitalIdAndBillingPeriod(UUID hospitalId, String billingPeriod);

    Page<HospitalServiceCharge> findByHospitalId(UUID hospitalId, Pageable pageable);

    Page<HospitalServiceCharge> findByBillingPeriod(String billingPeriod, Pageable pageable);

    Page<HospitalServiceCharge> findByStatus(PaymentStatus status, Pageable pageable);

    Page<HospitalServiceCharge> findByHospitalIdAndStatus(UUID hospitalId, PaymentStatus status, Pageable pageable);
}
