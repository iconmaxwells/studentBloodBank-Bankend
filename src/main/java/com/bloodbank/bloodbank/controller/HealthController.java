package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping({"/api/v1/health", "/health"})
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "ok", "db", "ok", "redis", "n/a"));
    }
}
