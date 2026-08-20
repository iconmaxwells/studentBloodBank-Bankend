package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.EarningsWithdrawal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EarningsWithdrawalRepository extends JpaRepository<EarningsWithdrawal, UUID> {
    Page<EarningsWithdrawal> findByDonorIdOrderByCreatedAtDesc(UUID donorId, Pageable pageable);
}
