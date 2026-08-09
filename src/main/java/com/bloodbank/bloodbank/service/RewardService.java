package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.DonorReward;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RewardLevel;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.DonorRewardRepository;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardService {

    private final DonorRewardRepository donorRewardRepository;
    private final DonorRepository donorRepository;

    public DonorReward getRewardsForDonor(UUID donorId) {
        authorizeDonorAccess(donorId);
        return donorRewardRepository.findByDonorId(donorId)
                .orElse(DonorReward.builder().donorId(donorId).build());
    }

    public List<Map<String, Object>> getTiers() {
        return List.of(
                tier(RewardLevel.Bronze, 0, "First-time donor badge"),
                tier(RewardLevel.Silver, 500, "Silver donor recognition"),
                tier(RewardLevel.Gold, 1000, "Gold donor certificate"),
                tier(RewardLevel.Platinum, 2000, "Platinum donor benefits"),
                tier(RewardLevel.Diamond, 5000, "Diamond lifetime recognition")
        );
    }

    private Map<String, Object> tier(RewardLevel level, int minPoints, String benefit) {
        return Map.of(
                "level", level.name(),
                "minPoints", minPoints,
                "benefit", benefit,
                "badges", List.of(level.name().toLowerCase() + "-badge")
        );
    }

    private void authorizeDonorAccess(UUID donorId) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role)) {
            donorRepository.findById(donorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Donor"));
            return;
        }
        if ("donor".equalsIgnoreCase(role)) {
            Donor donor = donorRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Donor"));
            if (!donor.getId().equals(donorId)) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            return;
        }
        throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
    }
}
