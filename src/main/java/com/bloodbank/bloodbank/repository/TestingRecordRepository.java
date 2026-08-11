package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.TestingRecord;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestingRecordRepository extends JpaRepository<TestingRecord, UUID> {
    Optional<TestingRecord> findByCollectionId(UUID collectionId);
    Page<TestingRecord> findByOverallStatus(TestOverallStatus status, Pageable pageable);
    List<TestingRecord> findByDonorIdAndOverallStatusIn(UUID donorId, Collection<TestOverallStatus> statuses);
}
