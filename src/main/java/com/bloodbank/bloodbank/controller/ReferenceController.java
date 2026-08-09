package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceDataService referenceDataService;

    @GetMapping("/blood-types")
    public ApiResponse<List<Map<String, Object>>> getBloodTypes() {
        return ApiResponse.ok(referenceDataService.getBloodTypes());
    }

    @GetMapping("/blood-groups")
    public ApiResponse<List<Map<String, String>>> getBloodGroups() {
        return ApiResponse.ok(referenceDataService.getBloodGroups());
    }

    @GetMapping("/regions")
    public ApiResponse<List<Map<String, String>>> getRegions() {
        return ApiResponse.ok(referenceDataService.getRegions());
    }

    @GetMapping("/urgency-levels")
    public ApiResponse<List<Map<String, Object>>> getUrgencyLevels() {
        return ApiResponse.ok(referenceDataService.getUrgencyLevels());
    }
}
