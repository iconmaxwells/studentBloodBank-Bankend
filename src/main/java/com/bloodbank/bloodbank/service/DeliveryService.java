package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.BloodRequest;
import com.bloodbank.bloodbank.entity.Delivery;
import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DeliveryStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.NotificationType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodRequestRepository;
import com.bloodbank.bloodbank.repository.DeliveryRepository;
import com.bloodbank.bloodbank.repository.HospitalRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final HospitalRepository hospitalRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> listDeliveries(UUID hospitalId, int page, int limit, String sort) {
        authorizeList(hospitalId);
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<Delivery> result = hospitalId != null
                ? deliveryRepository.findByHospitalId(hospitalId, pageable)
                : deliveryRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public Delivery getById(UUID id) {
        Delivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery"));
        authorizeDeliveryAccess(delivery);
        return delivery;
    }

    public Delivery scheduleDelivery(Delivery delivery) {
        requireStaff();
        BloodRequest request = bloodRequestRepository.findById(delivery.getRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Blood request"));
        if (request.getStatus() != RequestStatus.Approved && request.getStatus() != RequestStatus.Processing) {
            throw new BusinessRuleException("INVALID_STATUS", "Request must be approved before scheduling delivery");
        }
        delivery.setHospitalId(request.getHospitalId());
        delivery.setBloodGroup(request.getBloodGroup());
        delivery.setBloodProductType(request.getBloodProductType());
        delivery.setUnits(request.getUnitsFulfilled());
        delivery.setStatus(DeliveryStatus.Scheduled);
        Delivery saved = deliveryRepository.save(delivery);

        hospitalRepository.findById(request.getHospitalId()).ifPresent(h ->
                notificationService.notifyUser(h.getUser().getId(), NotificationType.info,
                        "Delivery scheduled", "Delivery scheduled for request " + request.getDisplayCode(),
                        "delivery", saved.getId().toString()));
        activityLogService.log(ActionType.create, "schedule_delivery", "Scheduled delivery for request",
                "delivery", request.getId(), null, request.getHospitalId(), null, null);
        return saved;
    }

    public Delivery updateDelivery(UUID id, Delivery updates) {
        requireStaff();
        Delivery delivery = getById(id);
        if (updates.getDeliveryDate() != null) delivery.setDeliveryDate(updates.getDeliveryDate());
        if (updates.getDeliveryTime() != null) delivery.setDeliveryTime(updates.getDeliveryTime());
        if (updates.getDeliveredBy() != null) delivery.setDeliveredBy(updates.getDeliveredBy());
        if (updates.getTemperature() != null) delivery.setTemperature(updates.getTemperature());
        if (updates.getCondition() != null) delivery.setCondition(updates.getCondition());
        if (updates.getNotes() != null) delivery.setNotes(updates.getNotes());
        if (updates.getStatus() != null) delivery.setStatus(updates.getStatus());
        return deliveryRepository.save(delivery);
    }

    public Delivery confirmReceipt(UUID id, String receivedBy) {
        if (!"hospital".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Hospital only", HttpStatus.FORBIDDEN);
        }
        Delivery delivery = getById(id);
        Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
        if (!delivery.getHospitalId().equals(hospital.getId())) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
        delivery.setStatus(DeliveryStatus.Delivered);
        delivery.setReceivedBy(receivedBy);
        Delivery saved = deliveryRepository.save(delivery);
        activityLogService.log(ActionType.update, "confirm_delivery", "Hospital confirmed delivery receipt",
                "delivery", delivery.getRequestId(), null, hospital.getId(), null, null);
        return saved;
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

    private void authorizeDeliveryAccess(Delivery delivery) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
            if (!delivery.getHospitalId().equals(hospital.getId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Staff required", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role);
    }
}
