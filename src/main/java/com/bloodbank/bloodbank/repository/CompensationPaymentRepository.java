package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompensationPaymentRepository extends JpaRepository<CompensationPayment, UUID> {
    Page<CompensationPayment> findByDonorId(UUID donorId, Pageable pageable);
    Page<CompensationPayment> findByStatus(PaymentStatus status, Pageable pageable);
}
