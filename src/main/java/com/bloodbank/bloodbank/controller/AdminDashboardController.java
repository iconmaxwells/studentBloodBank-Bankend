package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/api/v1/admin/dashboard/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.ok(adminDashboardService.getStats());
    }

    @GetMapping("/api/v1/admin/dashboard/charts")
    public ApiResponse<Map<String, Object>> getCharts() {
        return ApiResponse.ok(adminDashboardService.getCharts());
    }

    @GetMapping("/api/v1/admin/monitoring/inventory")
    public ApiResponse<List<Map<String, Object>>> getMonitoringInventory() {
        return ApiResponse.ok(adminDashboardService.getMonitoringInventory());
    }

    @GetMapping("/api/v1/admin/monitoring/alerts")
    public ApiResponse<List<Map<String, Object>>> getMonitoringAlerts() {
        return ApiResponse.ok(adminDashboardService.getMonitoringAlerts());
    }
}
