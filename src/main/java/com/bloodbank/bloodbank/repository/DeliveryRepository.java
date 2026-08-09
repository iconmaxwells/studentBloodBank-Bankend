package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Delivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
    Page<Delivery> findByHospitalId(UUID hospitalId, Pageable pageable);
    Page<Delivery> findByRequestId(UUID requestId, Pageable pageable);
}
