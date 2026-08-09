package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Page<Patient> findByHospitalId(UUID hospitalId, Pageable pageable);
}
