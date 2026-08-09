package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.DonorReward;
import com.bloodbank.bloodbank.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rewards")
@RequiredArgsConstructor
public class RewardsController {

    private final RewardService rewardService;

    @GetMapping("/tiers")
    public ApiResponse<List<Map<String, Object>>> getTiers() {
        return ApiResponse.ok(rewardService.getTiers());
    }

    @GetMapping("/{donorId}")
    public ApiResponse<DonorReward> getRewardsForDonor(@PathVariable UUID donorId) {
        return ApiResponse.ok(rewardService.getRewardsForDonor(donorId));
    }
}
