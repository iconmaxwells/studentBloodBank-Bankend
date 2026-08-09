package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.BloodRequest;
import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodRequestRepository;
import com.bloodbank.bloodbank.repository.HospitalRepository;
import com.bloodbank.bloodbank.repository.UserRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import com.bloodbank.bloodbank.websocket.LiveEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BloodRequestService {

    private final BloodRequestRepository bloodRequestRepository;
    private final HospitalRepository hospitalRepository;
    private final UserRepository userRepository;
    private final DisplayCodeService displayCodeService;
    private final InventoryService inventoryService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final LiveEventPublisher liveEventPublisher;

    @Transactional(readOnly = true)
    public Map<String, Object> listRequests(RequestStatus status, UUID hospitalId, int page, int limit, String sort) {
        authorizeList(hospitalId);
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<BloodRequest> result;
        if (hospitalId != null) {
            result = bloodRequestRepository.findByHospitalId(hospitalId, pageable);
        } else if (status != null) {
            result = bloodRequestRepository.findByStatus(status, pageable);
        } else {
            result = bloodRequestRepository.findAll(pageable);
        }
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichRequest)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    private Map<String, Object> enrichRequest(BloodRequest request) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", request.getId());
        row.put("displayCode", request.getDisplayCode());
        row.put("hospitalId", request.getHospitalId());
        row.put("bloodBankId", request.getBloodBankId());
        row.put("bloodGroup", request.getBloodGroup() != null ? request.getBloodGroup().getValue() : null);
        row.put("bloodProductType", request.getBloodProductType());
        row.put("unitsRequested", request.getUnitsRequested());
        row.put("unitsFulfilled", request.getUnitsFulfilled());
        row.put("urgency", request.getUrgency());
        row.put("status", request.getStatus());
        row.put("requestDate", request.getRequestDate());
        row.put("requiredBy", request.getRequiredBy());
        row.put("patientId", request.getPatientId());
        row.put("patientName", request.getPatientName());
        row.put("diagnosis", request.getDiagnosis());
        row.put("department", request.getDepartment());
        row.put("requestedBy", request.getRequestedBy());
        row.put("notes", request.getNotes());
        row.put("rejectionReason", request.getRejectionReason());
        row.put("approvedAt", request.getApprovedAt());
        row.put("completedAt", request.getCompletedAt());
        row.put("createdAt", request.getCreatedAt());
        row.put("updatedAt", request.getUpdatedAt());

        hospitalRepository.findById(request.getHospitalId()).ifPresent(hospital -> {
            row.put("hospitalName", hospital.getName());
            row.put("hospital", hospital.getName());
        });
        return row;
    }

    @Transactional(readOnly = true)
    public BloodRequest getById(UUID id) {
        BloodRequest request = bloodRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood request"));
        authorizeRequestAccess(request);
        return request;
    }

    public BloodRequest createRequest(BloodRequest request) {
        if (!"hospital".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Hospital only", HttpStatus.FORBIDDEN);
        }
        Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
        request.setHospitalId(hospital.getId());
        request.setDisplayCode(displayCodeService.nextCode(EntityType.REQUEST));
        request.setStatus(RequestStatus.Pending);
        request.setUnitsFulfilled(0);
        BloodRequest saved = bloodRequestRepository.save(request);

        hospital.setTotalRequests(safeCount(hospital.getTotalRequests()) + 1);
        hospital.setPendingRequests(safeCount(hospital.getPendingRequests()) + 1);
        hospitalRepository.save(hospital);

        notifySeniorStaff("New blood request", "Request " + saved.getDisplayCode() + " submitted.",
                "request", saved.getId().toString());
        activityLogService.log(ActionType.create, "create_request", "Hospital submitted request " + saved.getDisplayCode(),
                "request", saved.getId(), null, hospital.getId(), null, null);
        liveEventPublisher.requestCreated(Map.of(
                "requestId", saved.getId(),
                "displayCode", saved.getDisplayCode(),
                "urgency", saved.getUrgency(),
                "bloodGroup", saved.getBloodGroup() != null ? saved.getBloodGroup().getValue() : ""
        ));
        return saved;
    }

    public BloodRequest updateRequest(UUID id, BloodRequest updates) {
        BloodRequest request = getById(id);
        requireStaffOrAdmin();
        if (updates.getNotes() != null) request.setNotes(updates.getNotes());
        if (updates.getRequiredBy() != null) request.setRequiredBy(updates.getRequiredBy());
        if (updates.getUrgency() != null) request.setUrgency(updates.getUrgency());
        return bloodRequestRepository.save(request);
    }

    public BloodRequest approveRequest(UUID id) {
        requireApprovePermission();
        BloodRequest request = getById(id);
        if (request.getStatus() != RequestStatus.Pending) {
            throw new BusinessRuleException("INVALID_STATUS", "Only pending requests can be approved");
        }

        request.setStatus(RequestStatus.Approved);
        request.setApprovedBy(SecurityUtils.getCurrentUserId());
        request.setApprovedAt(LocalDateTime.now());
        BloodRequest saved = bloodRequestRepository.save(request);

        updateHospitalCounters(saved.getHospitalId(), -1);
        notifyHospital(saved.getHospitalId(), "Request approved",
                "Your request " + saved.getDisplayCode() + " has been approved.", saved.getId());
        activityLogService.log(ActionType.approve, "approve_request", "Approved request " + saved.getDisplayCode(),
                "request", saved.getId(), null, saved.getHospitalId(), null, null);
        publishStatusChange(saved);
        return saved;
    }

    public BloodRequest rejectRequest(UUID id, String reason) {
        requireRejectPermission();
        BloodRequest request = getById(id);
        if (request.getStatus() != RequestStatus.Pending) {
            throw new BusinessRuleException("INVALID_STATUS", "Only pending requests can be rejected");
        }
        request.setStatus(RequestStatus.Rejected);
        request.setRejectionReason(reason);
        BloodRequest saved = bloodRequestRepository.save(request);

        updateHospitalCounters(saved.getHospitalId(), -1);
        notifyHospital(saved.getHospitalId(), "Request rejected",
                "Your request " + saved.getDisplayCode() + " was rejected: " + reason, saved.getId());
        activityLogService.log(ActionType.reject, "reject_request", "Rejected request " + saved.getDisplayCode(),
                "request", saved.getId(), null, saved.getHospitalId(), null, null);
        return saved;
    }

    public BloodRequest processRequest(UUID id) {
        requireStaffOrAdmin();
        BloodRequest request = getById(id);
        if (request.getStatus() != RequestStatus.Approved) {
            throw new BusinessRuleException("INVALID_STATUS", "Only approved requests can be processed");
        }

        var allocated = inventoryService.allocateFifo(
                request.getBloodGroup(),
                request.getBloodProductType(),
                request.getUnitsRequested(),
                request.getId());

        request.setStatus(RequestStatus.Processing);
        request.setUnitsFulfilled(allocated.size());
        BloodRequest saved = bloodRequestRepository.save(request);
        notifyHospital(saved.getHospitalId(), "Request processing",
                "Your request " + saved.getDisplayCode() + " is being processed.", saved.getId());
        activityLogService.log(ActionType.update, "process_request", "Processing request " + saved.getDisplayCode(),
                "request", saved.getId(), null, saved.getHospitalId(), null, null);
        publishStatusChange(saved);
        return saved;
    }

    public BloodRequest completeRequest(UUID id) {
        requireStaffOrAdmin();
        BloodRequest request = getById(id);
        if (request.getStatus() != RequestStatus.Processing && request.getStatus() != RequestStatus.Approved) {
            throw new BusinessRuleException("INVALID_STATUS", "Request cannot be completed from current status");
        }
        inventoryService.issueAllocatedUnits(request.getId());
        request.setStatus(RequestStatus.Completed);
        request.setCompletedAt(LocalDateTime.now());
        BloodRequest saved = bloodRequestRepository.save(request);
        notifyHospital(saved.getHospitalId(), "Request completed",
                "Your request " + saved.getDisplayCode() + " has been completed.", saved.getId());
        activityLogService.log(ActionType.update, "complete_request", "Completed request " + saved.getDisplayCode(),
                "request", saved.getId(), null, saved.getHospitalId(), null, null);
        publishStatusChange(saved);
        liveEventPublisher.inventoryUpdated(Map.of("action", "issued", "requestId", saved.getId()));
        return saved;
    }

    private void updateHospitalCounters(UUID hospitalId, int pendingDelta) {
        hospitalRepository.findById(hospitalId).ifPresent(h -> {
            h.setPendingRequests(Math.max(0, safeCount(h.getPendingRequests()) + pendingDelta));
            hospitalRepository.save(h);
        });
    }

    private static int safeCount(Integer value) {
        return value != null ? value : 0;
    }

    private void notifyHospital(UUID hospitalId, String title, String message, UUID requestId) {
        hospitalRepository.findById(hospitalId).ifPresent(h ->
                notificationService.notifyUser(h.getUser().getId(), NotificationType.info, title, message,
                        "request", requestId.toString()));
    }

    private void notifySeniorStaff(String title, String message, String entityType, String entityId) {
        userRepository.findAll().stream()
                .filter(u -> "staff".equalsIgnoreCase(u.getRole().getName()) || "admin".equalsIgnoreCase(u.getRole().getName()))
                .forEach(u -> notificationService.notifyUser(u.getId(), NotificationType.urgent, title, message, entityType, entityId));
    }

    private void publishStatusChange(BloodRequest request) {
        liveEventPublisher.requestStatusChanged(Map.of(
                "requestId", request.getId(),
                "displayCode", request.getDisplayCode(),
                "status", request.getStatus().name()
        ));
    }

    private void authorizeList(UUID hospitalId) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
            if (hospitalId != null && !hospitalId.equals(hospital.getId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void authorizeRequestAccess(BloodRequest request) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
            if (!request.getHospitalId().equals(hospital.getId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireApprovePermission() {
        if ("admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) return;
        if (SecurityUtils.getCurrentUser() != null
                && SecurityUtils.getCurrentUser().hasPermission("canApproveRequests")) {
            return;
        }
        throw new ApiException("FORBIDDEN", "Insufficient permissions", HttpStatus.FORBIDDEN);
    }

    private void requireRejectPermission() {
        if ("admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) return;
        if (SecurityUtils.getCurrentUser() != null
                && SecurityUtils.getCurrentUser().hasPermission("canRejectRequests")) {
            return;
        }
        throw new ApiException("FORBIDDEN", "Insufficient permissions", HttpStatus.FORBIDDEN);
    }

    private void requireStaffOrAdmin() {
        if (!isStaffOrAdmin(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Staff or admin required", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role);
    }
}
