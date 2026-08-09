package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.service.HospitalPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/hospital")
@RequiredArgsConstructor
public class HospitalPortalController {

    private final HospitalPortalService hospitalPortalService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(hospitalPortalService.getDashboard());
    }

    @GetMapping("/profile")
    public ApiResponse<Hospital> getProfile() {
        return ApiResponse.ok(hospitalPortalService.getProfile());
    }

    @PatchMapping("/profile")
    public ApiResponse<Hospital> updateProfile(@Valid @RequestBody Hospital updates) {
        return ApiResponse.ok(hospitalPortalService.updateProfile(updates));
    }

    @GetMapping("/requests")
    public ApiResponse<?> getRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ControllerUtils.paged(hospitalPortalService.getRequests(page, limit, sort));
    }

    @GetMapping("/deliveries")
    public ApiResponse<?> getDeliveries(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        return ControllerUtils.paged(hospitalPortalService.getDeliveries(page, limit, sort));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> getNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ControllerUtils.paged(hospitalPortalService.getNotifications(page, limit));
    }

    @GetMapping("/inventory-availability")
    public ApiResponse<Map<String, Object>> getInventoryAvailability() {
        return ApiResponse.ok(hospitalPortalService.getInventoryPreview());
    }
}
