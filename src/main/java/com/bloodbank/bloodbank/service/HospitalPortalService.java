package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.dto.request.PayCompensationRequest;
import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodRequestRepository;
import com.bloodbank.bloodbank.repository.HospitalRepository;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HospitalPortalService {

    private final HospitalRepository hospitalRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final HospitalService hospitalService;
    private final BloodRequestService bloodRequestService;
    private final DeliveryService deliveryService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final HospitalBillingService hospitalBillingService;

    public Map<String, Object> getDashboard() {
        Hospital hospital = getCurrentHospital();
        Map<String, Object> stats = hospitalService.getStats(hospital.getId());
        long unread = notificationService.getUnreadCount();
        long pending = bloodRequestRepository.countByHospitalIdAndStatus(hospital.getId(), RequestStatus.Pending);

        return Map.of(
                "hospital", hospital,
                "stats", stats,
                "pendingRequests", pending,
                "unreadNotifications", unread
        );
    }

    public Hospital getProfile() {
        return getCurrentHospital();
    }

    @Transactional
    public Hospital updateProfile(Hospital updates) {
        Hospital hospital = getCurrentHospital();
        return hospitalService.updateHospital(hospital.getId(), updates);
    }

    public Map<String, Object> getRequests(int page, int limit, String sort) {
        Hospital hospital = getCurrentHospital();
        return bloodRequestService.listRequests(null, hospital.getId(), page, limit, sort);
    }

    public Map<String, Object> getDeliveries(int page, int limit, String sort) {
        Hospital hospital = getCurrentHospital();
        return deliveryService.listDeliveries(hospital.getId(), page, limit, sort);
    }

    public Map<String, Object> getInventoryPreview() {
        return Map.of("summary", inventoryService.getSummary());
    }

    public Map<String, Object> getNotifications(int page, int limit) {
        return notificationService.listNotifications(page, limit);
    }

    public Map<String, Object> getServiceCharges(int page, int limit, String sort) {
        Hospital hospital = getCurrentHospital();
        return hospitalBillingService.listCharges(hospital.getId(), null, null, page, limit, sort);
    }

    @Transactional
    public Map<String, Object> payServiceCharge(UUID chargeId, PayCompensationRequest request) {
        return hospitalBillingService.simulatePayCharge(chargeId, request);
    }

    private Hospital getCurrentHospital() {
        return hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
    }
}
