package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.RequestUnitAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RequestUnitAllocationRepository extends JpaRepository<RequestUnitAllocation, UUID> {
    List<RequestUnitAllocation> findByRequestId(UUID requestId);
}
