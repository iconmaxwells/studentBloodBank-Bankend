package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Collection;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CollectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {
    Optional<Collection> findByDisplayCode(String displayCode);
    Page<Collection> findByDonorId(UUID donorId, Pageable pageable);
    Page<Collection> findByStatus(CollectionStatus status, Pageable pageable);

    Optional<Collection> findFirstByDonorIdAndBloodGroupIsNotNullOrderByCollectionDateDesc(UUID donorId);
}
