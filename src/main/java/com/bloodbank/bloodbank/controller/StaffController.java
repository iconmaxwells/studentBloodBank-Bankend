package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.StaffCreateRequest;
import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.StaffRole;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffStatus;
import com.bloodbank.bloodbank.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) StaffStatus status) {
        return ControllerUtils.paged(staffService.listStaff(status, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Staff> getById(@PathVariable UUID id) {
        return ApiResponse.ok(staffService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Staff>> create(@Valid @RequestBody StaffCreateRequest request) {
        Staff staff = request.toStaff();
        if (request.getStaffRoleName() != null && staff.getStaffRole() == null) {
            StaffRole staffRole = staffService.resolveStaffRole(request.getStaffRoleName());
            staff.setStaffRole(staffRole);
        }
        Staff created = staffService.createStaff(
                staff, request.getEmail(), request.getPassword(), request.getPortalRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Staff> update(@PathVariable UUID id, @Valid @RequestBody Staff updates) {
        return ApiResponse.ok(staffService.updateStaff(id, updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable UUID id) {
        staffService.deleteStaff(id);
        return ApiResponse.ok(Map.of("message", "Staff deactivated successfully"));
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<Map<String, Boolean>> getPermissions(@PathVariable UUID id) {
        return ApiResponse.ok(staffService.getPermissions(id));
    }
}
