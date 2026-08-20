package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.WithdrawEarningsRequest;
import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.service.DonorPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/donor")
@RequiredArgsConstructor
public class DonorPortalController {

    private final DonorPortalService donorPortalService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(donorPortalService.getDashboard());
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile() {
        return ApiResponse.ok(donorPortalService.getProfile());
    }

    @PatchMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> updates) {
        return ApiResponse.ok(donorPortalService.updateProfile(updates));
    }

    @GetMapping("/donation-history")
    public ApiResponse<?> getDonationHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ControllerUtils.paged(donorPortalService.getDonationHistory(page, limit));
    }

    @GetMapping("/rewards")
    public ApiResponse<Map<String, Object>> getRewards() {
        return ApiResponse.ok(donorPortalService.getRewards());
    }

    @GetMapping("/earnings")
    public ApiResponse<Map<String, Object>> getEarnings() {
        return ApiResponse.ok(donorPortalService.getEarnings());
    }

    @PostMapping("/earnings/withdraw")
    public ApiResponse<Map<String, Object>> withdrawEarnings(@Valid @RequestBody WithdrawEarningsRequest request) {
        return ApiResponse.ok(donorPortalService.withdrawEarnings(request));
    }

    @PostMapping("/rewards/redeem")
    public ApiResponse<Map<String, Object>> redeemReward(@Valid @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(Map.of("status", "pending", "message", "Reward redemption not yet implemented"));
    }

    @GetMapping("/compensation")
    public ApiResponse<Map<String, Object>> getCompensation(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(donorPortalService.getCompensation(page, limit));
    }
}
