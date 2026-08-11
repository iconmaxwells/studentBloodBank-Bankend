package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.StaffStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {

    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StaffRoleRepository staffRoleRepository;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Map<String, Object> listStaff(StaffStatus status, int page, int limit, String sort) {
        requireAdmin();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        StaffStatus effectiveStatus = status != null ? status : StaffStatus.Active;
        Page<Staff> result = staffRepository.findByStatus(effectiveStatus, pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public Staff getById(UUID id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
        authorizeStaffAccess(staff);
        return staff;
    }

    public Staff createStaff(Staff staff, String email, String password, String portalRole) {
        requireAdmin();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("EMAIL_EXISTS", "Email already registered", HttpStatus.CONFLICT);
        }
        String roleName = portalRole != null ? portalRole : "staff";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ApiException("ROLE_NOT_FOUND", "Role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        if (staff.getStaffRole() == null) {
            String defaultStaffRole = "specialist".equalsIgnoreCase(roleName) ? "Senior Staff" : "Junior Staff";
            StaffRole staffRole = staffRoleRepository.findByName(defaultStaffRole)
                    .orElseGet(() -> staffRoleRepository.findByName("Junior Staff")
                            .orElseThrow(() -> new ApiException("ROLE_NOT_FOUND", "Staff role not found", HttpStatus.INTERNAL_SERVER_ERROR)));
            staff.setStaffRole(staffRole);
        }

        User user = userRepository.save(User.builder()
                .name(staff.getName())
                .email(email)
                .password(passwordEncoder.encode(password))
                .phone(staff.getPhone())
                .role(role)
                .active(true)
                .emailVerified(true)
                .build());

        staff.setUser(user);
        staff.setEmail(email);
        if (staff.getStatus() == null) {
            staff.setStatus(StaffStatus.Active);
        }
        Staff saved = staffRepository.save(staff);
        activityLogService.log(ActionType.create, "create_staff", "Created staff: " + saved.getName(),
                "staff", null, null, null, null, null);
        return saved;
    }

    public StaffRole resolveStaffRole(String name) {
        return staffRoleRepository.findByName(name)
                .orElseThrow(() -> new ApiException("ROLE_NOT_FOUND", "Staff role not found: " + name, HttpStatus.BAD_REQUEST));
    }

    public Staff updateStaff(UUID id, Staff updates) {
        requireAdmin();
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
        applyStaffUpdates(staff, updates);
        Staff saved = staffRepository.save(staff);
        activityLogService.log(ActionType.update, "update_staff", "Updated staff: " + saved.getName(),
                "staff", null, null, null, null, null);
        return saved;
    }

    public void deleteStaff(UUID id) {
        requireAdmin();
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
        staff.setStatus(StaffStatus.Inactive);
        staffRepository.save(staff);
        staff.getUser().setActive(false);
        userRepository.save(staff.getUser());
        activityLogService.log(ActionType.delete, "deactivate_staff", "Deactivated staff: " + staff.getName(),
                "staff", null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getPermissions(UUID staffId) {
        Staff staff = getById(staffId);
        return buildPermissions(staff);
    }

    @Transactional(readOnly = true)
    public Staff getByCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return staffRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
    }

    public Staff updateCurrentProfile(String email, String phone) {
        Staff staff = getByCurrentUser();
        if (email != null && !email.isBlank()) {
            staff.setEmail(email.trim());
            staff.getUser().setEmail(email.trim());
        }
        if (phone != null && !phone.isBlank()) {
            staff.setPhone(phone.trim());
            staff.getUser().setPhone(phone.trim());
        }
        userRepository.save(staff.getUser());
        return staffRepository.save(staff);
    }

    public Map<String, Boolean> getCurrentUserPermissions() {
        String role = SecurityUtils.getCurrentUserRole();
        if ("admin".equalsIgnoreCase(role)) {
            return adminPermissions();
        }
        Staff staff = getByCurrentUser();
        return buildPermissions(staff);
    }

    private Map<String, Boolean> buildPermissions(Staff staff) {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        if (staff.getStaffRole() != null) {
            staff.getStaffRole().getPermissions().forEach(p -> permissions.put(p.getName(), true));
        }
        return permissions;
    }

    private Map<String, Boolean> adminPermissions() {
        Map<String, Boolean> permissions = new LinkedHashMap<>();
        permissions.put("canApproveRequests", true);
        permissions.put("canRejectRequests", true);
        permissions.put("canManageInventory", true);
        permissions.put("canManageDonors", true);
        permissions.put("canConductWithdrawals", true);
        permissions.put("canViewReports", true);
        permissions.put("canManageStaff", true);
        return permissions;
    }

    private void applyStaffUpdates(Staff staff, Staff updates) {
        if (updates.getName() != null) staff.setName(updates.getName());
        if (updates.getPhone() != null) staff.setPhone(updates.getPhone());
        if (updates.getDepartment() != null) staff.setDepartment(updates.getDepartment());
        if (updates.getShift() != null) staff.setShift(updates.getShift());
        if (updates.getCertifications() != null) staff.setCertifications(updates.getCertifications());
        if (updates.getStatus() != null) staff.setStatus(updates.getStatus());
        if (updates.getStaffRole() != null) {
            StaffRole role = staffRoleRepository.findById(updates.getStaffRole().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff role"));
            staff.setStaffRole(role);
        }
    }

    private void authorizeStaffAccess(Staff staff) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("admin".equalsIgnoreCase(role)) {
            return;
        }
        if ("staff".equalsIgnoreCase(role) || "specialist".equalsIgnoreCase(role)) {
            if (!staff.getUser().getId().equals(SecurityUtils.getCurrentUserId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }
}
