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

    public BloodUnit createQuarantineUnitFromCollection(com.bloodbank.bloodbank.entity.Collection collection) {
        return bloodUnitRepository.findByCollectionId(collection.getId())
                .orElseGet(() -> saveNewUnitFromCollection(collection, UnitStatus.Quarantine));
    }

    public BloodUnit finalizeBloodUnitAfterTest(com.bloodbank.bloodbank.entity.Collection collection, boolean passed) {
        Optional<BloodUnit> existing = bloodUnitRepository.findByCollectionId(collection.getId());
        if (existing.isPresent()) {
            BloodUnit unit = existing.get();
            if (passed) {
                unit.setStatus(UnitStatus.Available);
                unit.setLocation(collection.getStorageLocation() != null
                        ? collection.getStorageLocation() : unit.getLocation());
            } else {
                unit.setStatus(UnitStatus.Discarded);
                unit.setDiscardedAt(LocalDateTime.now());
            }
            BloodUnit saved = bloodUnitRepository.save(unit);
            liveEventPublisher.inventoryUpdated(Map.of(
                    "unitId", saved.getId(),
                    "action", passed ? "released" : "discarded"));
            return saved;
        }
        if (passed) {
            return saveNewUnitFromCollection(collection, UnitStatus.Available);
        }
        return null;
    }

    /** @deprecated Prefer {@link #createQuarantineUnitFromCollection} and {@link #finalizeBloodUnitAfterTest}. */
    public BloodUnit createBloodUnitFromCollection(com.bloodbank.bloodbank.entity.Collection collection) {
        return finalizeBloodUnitAfterTest(collection, true);
    }

    private BloodUnit saveNewUnitFromCollection(com.bloodbank.bloodbank.entity.Collection collection,
                                                UnitStatus status) {
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
                .status(status)
                .location(collection.getStorageLocation())
                .build();
        BloodUnit saved = bloodUnitRepository.save(unit);
        liveEventPublisher.inventoryUpdated(Map.of("unitId", saved.getId(), "action", "created", "status", status));
        activityLogService.log(ActionType.collection, "inventory_unit_created",
                "Added blood unit " + saved.getId() + " to inventory (" + status + ")",
                "inventory", null, collection.getDonorId(), null, collection.getId(), null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSummary() {
        Map<String, Long> availableCounts = toCountMap(bloodUnitRepository.summarizeByGroupAndType(UnitStatus.Available));
        Map<String, Long> quarantineCounts = toCountMap(bloodUnitRepository.summarizeByGroupAndType(UnitStatus.Quarantine));
        List<Map<String, Object>> summary = new ArrayList<>();
        SystemSettings settings = systemSettingsService.getSettings();
        Map<String, Object> thresholds = settings.getInventoryThresholds() != null
                ? settings.getInventoryThresholds() : Map.of();

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(availableCounts.keySet());
        keys.addAll(quarantineCounts.keySet());

        for (String key : keys) {
            String[] parts = key.split("_", 2);
            if (parts.length < 2) continue;
            BloodGroup group = BloodGroup.fromValue(parts[0]);
            BloodProductType type = BloodProductType.fromValue(parts[1]);
            long available = availableCounts.getOrDefault(key, 0L);
            long quarantine = quarantineCounts.getOrDefault(key, 0L);
            int minThreshold = thresholds.containsKey(key) ? ((Number) thresholds.get(key)).intValue() : 5;
            String trend = available < minThreshold ? "critical" : available < minThreshold * 2 ? "down" : "stable";
            summary.add(Map.of(
                    "bloodGroup", group.getValue(),
                    "bloodProductType", type.getValue(),
                    "totalInStock", available + quarantine,
                    "availableUnits", available,
                    "releaseReadyUnits", available,
                    "quarantineUnits", quarantine,
                    "trend", trend
            ));
        }
        return summary;
    }

    private Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            BloodGroup group = (BloodGroup) row[0];
            BloodProductType type = (BloodProductType) row[1];
            long count = (Long) row[2];
            counts.put(group.getValue() + "_" + type.getValue(), count);
        }
        return counts;
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
        liveEventPublisher.inventoryUpdated(Map.of("unitId", unitId, "action", "released"));
        return saved;
    }

    public BloodUnit issueUnit(String unitId) {
        requireManageInventory();
        BloodUnit unit = getUnitById(unitId);
        if (unit.getStatus() != UnitStatus.Reserved && unit.getStatus() != UnitStatus.Available) {
            throw new BusinessRuleException("UNIT_NOT_ISSUABLE",
                    "Only available or reserved units can be issued");
        }
        unit.setStatus(UnitStatus.Issued);
        unit.setIssuedAt(LocalDateTime.now());
        unit.setReservedForRequestId(null);
        BloodUnit saved = bloodUnitRepository.save(unit);
        activityLogService.log(ActionType.update, "issue_unit", "Issued unit " + unitId,
                "inventory", null, null, null, null, null);
        liveEventPublisher.inventoryUpdated(Map.of("unitId", unitId, "action", "issued"));
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
        liveEventPublisher.inventoryUpdated(Map.of("unitId", unitId, "action", "discarded"));
        return saved;
    }

    @Transactional(readOnly = true)
    public long countAvailableUnits(BloodGroup bloodGroup, BloodProductType productType) {
        if (bloodGroup != null) {
            return bloodUnitRepository.countByBloodGroupAndBloodProductTypeAndStatus(
                    bloodGroup, productType, UnitStatus.Available);
        }
        return bloodUnitRepository.countByBloodProductTypeAndStatus(productType, UnitStatus.Available);
    }

    @Transactional(readOnly = true)
    public long countTotalInStock(BloodProductType productType) {
        return bloodUnitRepository.countByBloodProductTypeAndStatusIn(
                productType, List.of(UnitStatus.Available, UnitStatus.Quarantine));
    }

    /**
     * Adds received partner-bank units to local inventory when a supply transfer is delivered.
     */
    public void receiveSupplyTransfer(SupplyRequest request) {
        if (Boolean.TRUE.equals(request.getInventoryApplied())) {
            return;
        }
        if (request.getBloodProductType() == null || request.getUnitsRequested() == null) {
            throw new BusinessRuleException("INVALID_SUPPLY_REQUEST", "Supply request is missing product details");
        }
        BloodGroup group = request.getBloodGroup() != null ? request.getBloodGroup() : BloodGroup.O_POSITIVE;
        BloodProductType productType = request.getBloodProductType();
        int count = request.getUnitsRequested();
        String location = "Transfer Receipt"
                + (request.getSupplierBloodBankName() != null ? " — " + request.getSupplierBloodBankName() : "");

        List<String> createdIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String unitId = displayCodeService.nextBloodUnitCode(productType);
            LocalDate collectionDate = LocalDate.now();
            BloodUnit unit = BloodUnit.builder()
                    .id(unitId)
                    .bloodGroup(group)
                    .bloodProductType(productType)
                    .collectionDate(collectionDate)
                    .expiryDate(BloodBankUtils.calculateExpiryDate(collectionDate, productType))
                    .status(UnitStatus.Available)
                    .location(location)
                    .build();
            bloodUnitRepository.save(unit);
            createdIds.add(unitId);
        }

        request.setInventoryApplied(true);
        activityLogService.log(ActionType.update, "supply_transfer_received",
                "Received " + count + " " + group.getValue() + " " + productType.getValue()
                        + " units from supply request " + request.getDisplayCode(),
                "inventory", request.getId(), null, null, null, null);
        liveEventPublisher.inventoryUpdated(Map.of(
                "action", "transfer_received",
                "supplyRequestId", request.getId(),
                "unitsAdded", count,
                "bloodGroup", group.getValue(),
                "bloodProductType", productType.getValue(),
                "unitIds", createdIds));
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
            liveEventPublisher.inventoryUpdated(Map.of(
                    "unitId", unit.getId(), "action", "reserved", "requestId", requestId));
        }
        liveEventPublisher.inventoryUpdated(Map.of(
                "action", "allocated", "requestId", requestId, "unitsReserved", allocated.size()));
        return allocated;
    }

    public void issueAllocatedUnits(UUID requestId) {
        List<RequestUnitAllocation> allocations = allocationRepository.findByRequestId(requestId);
        for (RequestUnitAllocation allocation : allocations) {
            BloodUnit unit = bloodUnitRepository.findById(allocation.getBloodUnitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Blood unit"));
            unit.setStatus(UnitStatus.Issued);
            unit.setIssuedAt(LocalDateTime.now());
            unit.setReservedForRequestId(null);
            bloodUnitRepository.save(unit);
            liveEventPublisher.inventoryUpdated(Map.of(
                    "unitId", unit.getId(), "action", "issued", "requestId", requestId));
        }
        if (!allocations.isEmpty()) {
            liveEventPublisher.inventoryUpdated(Map.of(
                    "action", "request_issued", "requestId", requestId, "unitsIssued", allocations.size()));
        }
    }

    public void releaseAllocatedUnits(UUID requestId) {
        List<RequestUnitAllocation> allocations = allocationRepository.findByRequestId(requestId);
        for (RequestUnitAllocation allocation : allocations) {
            bloodUnitRepository.findById(allocation.getBloodUnitId()).ifPresent(unit -> {
                unit.setStatus(UnitStatus.Available);
                unit.setReservedForRequestId(null);
                bloodUnitRepository.save(unit);
                liveEventPublisher.inventoryUpdated(Map.of(
                        "unitId", unit.getId(), "action", "released", "requestId", requestId));
            });
        }
        if (!allocations.isEmpty()) {
            allocationRepository.deleteAll(allocations);
            liveEventPublisher.inventoryUpdated(Map.of(
                    "action", "allocation_released", "requestId", requestId));
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
