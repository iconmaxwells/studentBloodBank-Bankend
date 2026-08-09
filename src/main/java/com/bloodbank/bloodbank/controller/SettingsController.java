package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.SystemSettings;
import com.bloodbank.bloodbank.service.SystemSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping("/system")
    public ApiResponse<SystemSettings> getSystemSettings() {
        return ApiResponse.ok(systemSettingsService.getSettings());
    }

    @PatchMapping("/system")
    public ApiResponse<SystemSettings> updateSystemSettings(@Valid @RequestBody SystemSettings updates) {
        return ApiResponse.ok(systemSettingsService.updateSettings(updates));
    }

    @GetMapping("/notifications")
    public ApiResponse<Map<String, Object>> getNotificationSettings() {
        SystemSettings settings = systemSettingsService.getSettings();
        return ApiResponse.ok(settings.getNotificationPreferences());
    }

    @PatchMapping("/notifications")
    public ApiResponse<Map<String, Object>> updateNotificationSettings(@Valid @RequestBody Map<String, Object> preferences) {
        SystemSettings updates = SystemSettings.builder().notificationPreferences(preferences).build();
        return ApiResponse.ok(systemSettingsService.updateSettings(updates).getNotificationPreferences());
    }

    @GetMapping("/security")
    public ApiResponse<Map<String, Object>> getSecuritySettings() {
        SystemSettings settings = systemSettingsService.getSettings();
        return ApiResponse.ok(settings.getSecuritySettings());
    }

    @PatchMapping("/security")
    public ApiResponse<Map<String, Object>> updateSecuritySettings(@Valid @RequestBody Map<String, Object> securitySettings) {
        SystemSettings updates = SystemSettings.builder().securitySettings(securitySettings).build();
        return ApiResponse.ok(systemSettingsService.updateSettings(updates).getSecuritySettings());
    }
}
