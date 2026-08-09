package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.Role;
import com.bloodbank.bloodbank.entity.User;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodRequestRepository;
import com.bloodbank.bloodbank.repository.HospitalRepository;
import com.bloodbank.bloodbank.repository.RoleRepository;
import com.bloodbank.bloodbank.repository.UserRepository;
import com.bloodbank.bloodbank.util.BloodBankUtils;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DisplayCodeService displayCodeService;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Map<String, Object> listHospitals(String search, HospitalStatus status, int page, int limit, String sort) {
        requireStaffOrAdmin();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        String pattern = toSearchPattern(search);
        Page<Hospital> result = hospitalRepository.search(pattern, status, pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public Hospital getById(UUID id) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
        authorizeHospitalAccess(hospital);
        return hospital;
    }

    @Transactional(readOnly = true)
    public Hospital getByDisplayCode(String displayCode) {
        Hospital hospital = hospitalRepository.findByDisplayCode(displayCode)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
        authorizeHospitalAccess(hospital);
        return hospital;
    }

    public Hospital createHospital(Hospital hospital, String email, String password) {
        requireAdmin();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("EMAIL_EXISTS", "Email already registered", HttpStatus.CONFLICT);
        }
        if (hospital.getRegistrationNumber() != null
                && hospitalRepository.existsByRegistrationNumber(hospital.getRegistrationNumber())) {
            throw new ApiException("REG_EXISTS", "Registration number already exists", HttpStatus.CONFLICT);
        }
        Role role = roleRepository.findByName("hospital")
                .orElseThrow(() -> new ApiException("ROLE_NOT_FOUND", "Hospital role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        User user = userRepository.save(User.builder()
                .name(hospital.getName())
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .emailVerified(true)
                .build());

        hospital.setDisplayCode(displayCodeService.nextCode(EntityType.HOSPITAL));
        hospital.setUser(user);
        if (hospital.getStatus() == null) {
            hospital.setStatus(HospitalStatus.Active);
        }
        Hospital saved = hospitalRepository.save(hospital);
        activityLogService.log(ActionType.create, "create_hospital", "Created hospital: " + saved.getDisplayCode(),
                "hospital", null, null, saved.getId(), null, null);
        return saved;
    }

    public Hospital updateHospital(UUID id, Hospital updates) {
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            authorizeHospitalAccess(hospital);
        } else {
            requireAdmin();
        }
        applyHospitalUpdates(hospital, updates);
        Hospital saved = hospitalRepository.save(hospital);
        activityLogService.log(ActionType.update, "update_hospital", "Updated hospital: " + saved.getDisplayCode(),
                "hospital", null, null, saved.getId(), null, null);
        return saved;
    }

    public void deleteHospital(UUID id) {
        requireAdmin();
        Hospital hospital = hospitalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
        hospitalRepository.delete(hospital);
        userRepository.delete(hospital.getUser());
        activityLogService.log(ActionType.delete, "delete_hospital", "Soft deleted hospital: " + hospital.getDisplayCode(),
                "hospital", null, null, hospital.getId(), null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(UUID hospitalId) {
        Hospital hospital = getById(hospitalId);
        long total = bloodRequestRepository.findByHospitalId(hospital.getId(), PageRequest.of(0, 1)).getTotalElements();
        long pending = bloodRequestRepository.countByHospitalIdAndStatus(hospital.getId(), RequestStatus.Pending);
        long approved = bloodRequestRepository.countByHospitalIdAndStatus(hospital.getId(), RequestStatus.Approved);
        long completed = bloodRequestRepository.countByHospitalIdAndStatus(hospital.getId(), RequestStatus.Completed);
        long rejected = bloodRequestRepository.countByHospitalIdAndStatus(hospital.getId(), RequestStatus.Rejected);
        double approvalRate = total > 0 ? (double) (approved + completed) / total * 100 : 0;

        return Map.of(
                "hospitalId", hospital.getId(),
                "displayCode", hospital.getDisplayCode(),
                "totalRequests", total,
                "pendingRequests", pending,
                "approvedRequests", approved,
                "completedRequests", completed,
                "rejectedRequests", rejected,
                "approvalRate", Math.round(approvalRate * 10.0) / 10.0
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findNearby(double lat, double lng, double radiusKm) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Hospital hospital : hospitalRepository.findAll()) {
            if (hospital.getLatitude() == null || hospital.getLongitude() == null) {
                continue;
            }
            double distance = BloodBankUtils.haversineKm(lat, lng, hospital.getLatitude(), hospital.getLongitude());
            if (distance <= radiusKm) {
                results.add(Map.of(
                        "hospital", hospital,
                        "distanceKm", Math.round(distance * 100.0) / 100.0
                ));
            }
        }
        results.sort(Comparator.comparingDouble(m -> (Double) m.get("distanceKm")));
        return results;
    }

    @Transactional(readOnly = true)
    public Hospital getByCurrentUser() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return hospitalRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
    }

    private void applyHospitalUpdates(Hospital hospital, Hospital updates) {
        if (updates.getName() != null) hospital.setName(updates.getName());
        if (updates.getRegistrationNumber() != null) hospital.setRegistrationNumber(updates.getRegistrationNumber());
        if (updates.getLocation() != null) hospital.setLocation(updates.getLocation());
        if (updates.getAddress() != null) hospital.setAddress(updates.getAddress());
        if (updates.getLatitude() != null) hospital.setLatitude(updates.getLatitude());
        if (updates.getLongitude() != null) hospital.setLongitude(updates.getLongitude());
        if (updates.getPhone() != null) hospital.setPhone(updates.getPhone());
        if (updates.getEmergencyPhone() != null) hospital.setEmergencyPhone(updates.getEmergencyPhone());
        if (updates.getEmail() != null) hospital.setEmail(updates.getEmail());
        if (updates.getWebsite() != null) hospital.setWebsite(updates.getWebsite());
        if (updates.getCapacity() != null) hospital.setCapacity(updates.getCapacity());
        if (updates.getBeds() != null) hospital.setBeds(updates.getBeds());
        if (updates.getDepartments() != null) hospital.setDepartments(updates.getDepartments());
        if (updates.getPrimaryContact() != null) hospital.setPrimaryContact(updates.getPrimaryContact());
        if (updates.getBloodBankCoordinator() != null) hospital.setBloodBankCoordinator(updates.getBloodBankCoordinator());
        if (updates.getOperatingHours() != null) hospital.setOperatingHours(updates.getOperatingHours());
        if (updates.getAccreditation() != null) hospital.setAccreditation(updates.getAccreditation());
        if (updates.getLicenses() != null) hospital.setLicenses(updates.getLicenses());
        if (updates.getStatus() != null && !"hospital".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            hospital.setStatus(updates.getStatus());
        }
    }

    private void authorizeHospitalAccess(Hospital hospital) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            if (!hospital.getUser().getId().equals(SecurityUtils.getCurrentUserId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStaffOrAdmin() {
        if (!isStaffOrAdmin(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Staff or admin required", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role);
    }

    private static String toSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase() + "%";
    }
}
