package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.BloodBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BloodBankRepository extends JpaRepository<BloodBank, UUID> {
    Optional<BloodBank> findByDisplayCode(String displayCode);
}
