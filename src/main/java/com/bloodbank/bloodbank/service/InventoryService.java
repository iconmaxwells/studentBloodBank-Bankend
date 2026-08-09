package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodUnitRepository;
import com.bloodbank.bloodbank.repository.RequestUnitAllocationRepository;
import com.bloodbank.bloodbank.util.BloodBankUtils;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import com.bloodbank.bloodbank.websocket.LiveEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final BloodUnitRepository bloodUnitRepository;
    private final RequestUnitAllocationRepository allocationRepository;
    private final DisplayCodeService displayCodeService;
    private final SystemSettingsService systemSettingsService;
    private final ActivityLogService activityLogService;
    private final LiveEventPublisher liveEventPublisher;

    public BloodUnit createBloodUnitFromCollection(com.bloodbank.bloodbank.entity.Collection collection) {
        String unitId = displayCodeService.nextBloodUnitCode(collection.getBloodProductType());
        LocalDate collectionDate = collection.getCollectionDate() != null
                ? collection.getCollectionDate() : LocalDate.now();
        BloodUnit unit = BloodUnit.builder()
                .id(unitId)
                .collectionId(collection.getId())
                .donorId(collection.getDonorId())
                .bloodGroup(collection.getBloodGroup())
                .bloodProductType(collection.getBloodProductType())
                .collectionDate(collectionDate)
                .expiryDate(BloodBankUtils.calculateExpiryDate(collectionDate, collection.getBloodProductType()))
                .status(UnitStatus.Available)
                .location(collection.getStorageLocation())
                .build();
        BloodUnit saved = bloodUnitRepository.save(unit);
        liveEventPublisher.inventoryUpdated(Map.of("unitId", saved.getId(), "action", "created"));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSummary() {
        List<Object[]> rows = bloodUnitRepository.summarizeByGroupAndType(UnitStatus.Available);
        List<Map<String, Object>> summary = new ArrayList<>();
        SystemSettings settings = systemSettingsService.getSettings();
        Map<String, Object> thresholds = settings.getInventoryThresholds() != null
                ? settings.getInventoryThresholds() : Map.of();

        for (Object[] row : rows) {
            BloodGroup group = (BloodGroup) row[0];
            BloodProductType type = (BloodProductType) row[1];
            long count = (Long) row[2];
            String key = group.getValue() + "_" + type.getValue();
            int minThreshold = thresholds.containsKey(key) ? ((Number) thresholds.get(key)).intValue() : 5;
            String trend = count < minThreshold ? "critical" : count < minThreshold * 2 ? "down" : "stable";
            summary.add(Map.of(
                    "bloodGroup", group.getValue(),
                    "bloodProductType", type.getValue(),
                    "availableUnits", count,
                    "trend", trend
            ));
        }
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listUnits(UnitStatus status, int page, int limit, String sort) {
        requireInventoryAccess();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<BloodUnit> result = status != null
                ? bloodUnitRepository.findByStatus(status, pageable)
                : bloodUnitRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public BloodUnit getUnitById(String id) {
        requireInventoryAccess();
        return bloodUnitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood unit"));
    }

    public BloodUnit updateUnit(String id, BloodUnit updates) {
        requireManageInventory();
        BloodUnit unit = getUnitById(id);
        if (updates.getLocation() != null) unit.setLocation(updates.getLocation());
        if (updates.getStatus() != null) unit.setStatus(updates.getStatus());
        return bloodUnitRepository.save(unit);
    }

    public BloodUnit reserveUnit(String unitId, UUID requestId) {
        requireManageInventory();
        BloodUnit unit = getUnitById(unitId);
        if (unit.getStatus() != UnitStatus.Available) {
            throw new BusinessRuleException("UNIT_NOT_AVAILABLE", "Unit is not available for reservation");
        }
        unit.setStatus(UnitStatus.Reserved);
        unit.setReservedForRequestId(requestId);
        BloodUnit saved = bloodUnitRepository.save(unit);
        activityLogService.log(ActionType.update, "reserve_unit", "Reserved unit " + unitId,
                "inventory", requestId, null, null, null, null);
        liveEventPublisher.inventoryUpdated(Map.of("unitId", unitId, "action", "reserved", "requestId", requestId));
        return saved;
    }

    public BloodUnit releaseUnit(String unitId) {
        requireManageInventory();
        BloodUnit unit = getUnitById(unitId);
        if (unit.getStatus() != UnitStatus.Reserved) {
            throw new BusinessRuleException("UNIT_NOT_RESERVED", "Unit is not reserved");
        }
        unit.setStatus(UnitStatus.Available);
        unit.setReservedForRequestId(null);
        BloodUnit saved = bloodUnitRepository.save(unit);
        activityLogService.log(ActionType.update, "release_unit", "Released unit " + unitId,
                "inventory", null, null, null, null, null);
        return saved;
    }

    public BloodUnit discardUnit(String unitId, String reason) {
        requireManageInventory();
        BloodUnit unit = getUnitById(unitId);
        unit.setStatus(UnitStatus.Discarded);
        unit.setDiscardedAt(LocalDateTime.now());
        unit.setReservedForRequestId(null);
        BloodUnit saved = bloodUnitRepository.save(unit);
        activityLogService.log(ActionType.update, "discard_unit", "Discarded unit " + unitId + ": " + reason,
                "inventory", null, null, null, null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BloodUnit> getExpiringUnits(int withinDays) {
        requireInventoryAccess();
        LocalDate today = LocalDate.now();
        return bloodUnitRepository.findByExpiryDateBetweenAndStatus(today, today.plusDays(withinDays), UnitStatus.Available);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAlerts() {
        requireInventoryAccess();
        List<Map<String, Object>> alerts = new ArrayList<>();
        SystemSettings settings = systemSettingsService.getSettings();
        Map<String, Object> thresholds = settings.getInventoryThresholds() != null
                ? settings.getInventoryThresholds() : Map.of();

        for (Map<String, Object> item : getSummary()) {
            if ("critical".equals(item.get("trend"))) {
                alerts.add(Map.of(
                        "type", "critical",
                        "bloodGroup", item.get("bloodGroup"),
                        "bloodProductType", item.get("bloodProductType"),
                        "availableUnits", item.get("availableUnits"),
                        "message", "Stock below minimum threshold"
                ));
            }
        }

        for (BloodUnit unit : getExpiringUnits(2)) {
            alerts.add(Map.of(
                    "type", "warning",
                    "unitId", unit.getId(),
                    "bloodGroup", unit.getBloodGroup().getValue(),
                    "expiryDate", unit.getExpiryDate(),
                    "message", "Unit expiring within 48 hours"
            ));
        }
        return alerts;
    }

    public List<BloodUnit> allocateFifo(BloodGroup bloodGroup, BloodProductType productType,
                                        int unitsNeeded, UUID requestId) {
        requireManageInventory();
        List<BloodUnit> available = bloodUnitRepository
                .findByBloodGroupAndBloodProductTypeAndStatusOrderByExpiryDateAsc(
                        bloodGroup, productType, UnitStatus.Available);

        if (available.size() < unitsNeeded) {
            Set<BloodGroup> compatible = BloodBankUtils.compatibleGroups(bloodGroup);
            for (BloodGroup alt : compatible) {
                if (alt == bloodGroup) continue;
                available.addAll(bloodUnitRepository
                        .findByBloodGroupAndBloodProductTypeAndStatusOrderByExpiryDateAsc(
                                alt, productType, UnitStatus.Available));
            }
            available.sort(Comparator.comparing(BloodUnit::getExpiryDate));
        }

        if (available.size() < unitsNeeded) {
            throw new BusinessRuleException("INSUFFICIENT_INVENTORY",
                    "Not enough " + bloodGroup.getValue() + " " + productType.getValue()
                            + " units available. Requested " + unitsNeeded + ", available " + available.size());
        }

        List<BloodUnit> allocated = new ArrayList<>();
        for (int i = 0; i < unitsNeeded; i++) {
            BloodUnit unit = available.get(i);
            unit.setStatus(UnitStatus.Reserved);
            unit.setReservedForRequestId(requestId);
            bloodUnitRepository.save(unit);
            allocationRepository.save(RequestUnitAllocation.builder()
                    .requestId(requestId)
                    .bloodUnitId(unit.getId())
                    .allocatedBy(SecurityUtils.getCurrentUserId())
                    .build());
            allocated.add(unit);
        }
        return allocated;
    }

    public void issueAllocatedUnits(UUID requestId) {
        List<RequestUnitAllocation> allocations = allocationRepository.findByRequestId(requestId);
        for (RequestUnitAllocation allocation : allocations) {
            BloodUnit unit = bloodUnitRepository.findById(allocation.getBloodUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Blood unit"));
            unit.setStatus(UnitStatus.Issued);
            unit.setIssuedAt(LocalDateTime.now());
            bloodUnitRepository.save(unit);
        }
    }

    public void releaseAllocatedUnits(UUID requestId) {
        List<RequestUnitAllocation> allocations = allocationRepository.findByRequestId(requestId);
        for (RequestUnitAllocation allocation : allocations) {
            bloodUnitRepository.findById(allocation.getBloodUnitId()).ifPresent(unit -> {
                unit.setStatus(UnitStatus.Available);
                unit.setReservedForRequestId(null);
                bloodUnitRepository.save(unit);
            });
        }
    }

    private void requireInventoryAccess() {
        String role = SecurityUtils.getCurrentUserRole();
        if ("admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role)
                || "hospital".equalsIgnoreCase(role)) {
            return;
        }
        throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
    }

    private void requireManageInventory() {
        if ("admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) return;
        if (SecurityUtils.getCurrentUser() != null
                && SecurityUtils.getCurrentUser().hasPermission("canManageInventory")) {
            return;
        }
        throw new ApiException("FORBIDDEN", "Insufficient permissions", HttpStatus.FORBIDDEN);
    }
}
