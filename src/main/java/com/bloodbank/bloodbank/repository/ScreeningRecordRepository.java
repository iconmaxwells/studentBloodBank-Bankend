package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.ScreeningRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ScreeningRecordRepository extends JpaRepository<ScreeningRecord, UUID> {
    Page<ScreeningRecord> findByDonorId(UUID donorId, Pageable pageable);

    java.util.Optional<ScreeningRecord> findFirstByDonorIdAndBloodGroupIsNotNullOrderByUpdatedAtDesc(UUID donorId);

    java.util.Optional<ScreeningRecord> findFirstByDonorIdAndStatusOrderByUpdatedAtDesc(
            UUID donorId, com.bloodbank.bloodbank.entity.enums.DomainEnums.ScreeningStatus status);

    java.util.Optional<ScreeningRecord> findFirstByDonorIdOrderByUpdatedAtDesc(UUID donorId);
}
