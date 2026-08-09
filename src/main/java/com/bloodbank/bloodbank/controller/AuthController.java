package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.*;
import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register/donor")
    public ResponseEntity<ApiResponse<LoginResponse>> registerDonor(@Valid @RequestBody DonorRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authenticationService.registerDonor(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authenticationService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authenticationService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout() {
        authenticationService.logout();
        return ApiResponse.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(request);
        return ApiResponse.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/verify-action")
    public ApiResponse<Map<String, Object>> verifyAction(@Valid @RequestBody VerifyActionRequest request) {
        return ApiResponse.ok(authenticationService.verifyAction(request));
    }

    @PostMapping("/verify-staff-action")
    public ApiResponse<Map<String, Object>> verifyStaffAction(@Valid @RequestBody VerifyActionRequest request) {
        return ApiResponse.ok(authenticationService.verifyAction(request));
    }

    @PostMapping("/verify-admin-password")
    public ApiResponse<Map<String, Object>> verifyAdminPassword(@Valid @RequestBody VerifyActionRequest request) {
        return ApiResponse.ok(authenticationService.verifyAdminPassword(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(authenticationService.me());
    }
}
