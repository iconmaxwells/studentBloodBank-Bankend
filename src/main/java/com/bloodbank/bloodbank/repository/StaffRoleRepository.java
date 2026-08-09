package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffRoleRepository extends JpaRepository<StaffRole, Long> {
    Optional<StaffRole> findByName(String name);
}
