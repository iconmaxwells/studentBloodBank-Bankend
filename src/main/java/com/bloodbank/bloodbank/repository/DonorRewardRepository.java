package com.bloodbank.bloodbank.repository;

import com.bloodbank.bloodbank.entity.DonorReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DonorRewardRepository extends JpaRepository<DonorReward, UUID> {
    Optional<DonorReward> findByDonorId(UUID donorId);
}
