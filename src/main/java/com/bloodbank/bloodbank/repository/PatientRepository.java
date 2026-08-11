package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Page<Patient> findByHospitalId(UUID hospitalId, Pageable pageable);

    Optional<Patient> findFirstByHospitalIdAndExternalId(UUID hospitalId, String externalId);

    Optional<Patient> findFirstByHospitalIdAndNameIgnoreCase(UUID hospitalId, String name);
}
