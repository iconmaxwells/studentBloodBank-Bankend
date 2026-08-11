package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.BloodBank;
import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.SupplyRequest;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.SupplyRequestStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Urgency;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodBankRepository;
import com.bloodbank.bloodbank.repository.SupplyRequestRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
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
public class SupplyRequestService {

    private final SupplyRequestRepository supplyRequestRepository;
    private final BloodBankRepository bloodBankRepository;
    private final DisplayCodeService displayCodeService;
    private final StaffService staffService;

    @Transactional(readOnly = true)
    public Map<String, Object> listRequests(SupplyRequestStatus status, int page, int limit, String sort) {
        authorizeAccess();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<SupplyRequest> result = status != null
                ? supplyRequestRepository.findByStatus(status, pageable)
                : supplyRequestRepository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichRequest)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRequest(UUID id) {
        authorizeAccess();
        return enrichRequest(findById(id));
    }

    public Map<String, Object> createRequest(Map<String, Object> body) {
        authorizeAccess();
        BloodGroup bloodGroup = parseBloodGroup(body.get("bloodGroup"));
        BloodProductType productType = parseBloodProductType(body.get("bloodProductType"));
        Integer unitsRequested = parseInteger(body.get("unitsRequested"), "unitsRequested");
        Urgency urgency = parseUrgency(body.get("urgency"));
        UUID supplierId = parseUuid(body.get("supplierBloodBankId"), "supplierBloodBankId");
        LocalDate requiredBy = parseDate(body.get("requiredBy"), "requiredBy");
        String reason = requireString(body.get("reason"), "reason");

        BloodBank supplier = bloodBankRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier blood bank not found"));

        Staff staff = resolveCurrentStaff();

        SupplyRequest request = SupplyRequest.builder()
                .displayCode(displayCodeService.nextCode(EntityType.SUPPLY_REQUEST))
                .bloodGroup(bloodGroup)
                .bloodProductType(productType)
                .unitsRequested(unitsRequested)
                .urgency(urgency)
                .status(SupplyRequestStatus.Submitted)
                .supplierBloodBankId(supplier.getId())
                .supplierBloodBankName(supplier.getName())
                .requiredBy(requiredBy)
                .reason(reason)
                .currentUnits(parseOptionalInteger(body.get("currentUnits")))
                .capacity(parseOptionalInteger(body.get("capacity")))
                .requestedById(staff != null ? staff.getUser().getId() : SecurityUtils.getCurrentUserId())
                .requestedByName(staff != null ? staff.getName() : SecurityUtils.getCurrentUser().getUsername())
                .followUpNotes(new ArrayList<>())
                .build();

        addFollowUpEntry(request, "Request submitted to " + supplier.getName(), SupplyRequestStatus.Submitted.name());

        SupplyRequest saved = supplyRequestRepository.save(request);
        return enrichRequest(saved);
    }

    public Map<String, Object> updateStatus(UUID id, SupplyRequestStatus status, String note) {
        authorizeAccess();
        SupplyRequest request = findById(id);
        if (status == null) {
            throw new ApiException("BAD_REQUEST", "Status is required", HttpStatus.BAD_REQUEST);
        }
        request.setStatus(status);
        String message = note != null && !note.isBlank()
                ? note
                : "Status updated to " + status.name().replace('_', ' ');
        addFollowUpEntry(request, message, status.name());
        return enrichRequest(supplyRequestRepository.save(request));
    }

    public Map<String, Object> addFollowUp(UUID id, String note) {
        authorizeAccess();
        if (note == null || note.isBlank()) {
            throw new ApiException("BAD_REQUEST", "Follow-up note is required", HttpStatus.BAD_REQUEST);
        }
        SupplyRequest request = findById(id);
        addFollowUpEntry(request, note, request.getStatus().name());
        return enrichRequest(supplyRequestRepository.save(request));
    }

    private SupplyRequest findById(UUID id) {
        return supplyRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supply request not found"));
    }

    private void addFollowUpEntry(SupplyRequest request, String note, String status) {
        if (request.getFollowUpNotes() == null) {
            request.setFollowUpNotes(new ArrayList<>());
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("authorName", resolveAuthorName());
        entry.put("note", note);
        entry.put("status", status);
        request.getFollowUpNotes().add(entry);
    }

    private String resolveAuthorName() {
        Staff staff = resolveCurrentStaff();
        if (staff != null) {
            return staff.getName();
        }
        var user = SecurityUtils.getCurrentUser();
        return user != null ? user.getUsername() : "Staff";
    }

    private Staff resolveCurrentStaff() {
        try {
            return staffService.getByCurrentUser();
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> enrichRequest(SupplyRequest request) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", request.getId());
        row.put("displayCode", request.getDisplayCode());
        row.put("bloodGroup", request.getBloodGroup() != null ? request.getBloodGroup().getValue() : null);
        row.put("bloodProductType", request.getBloodProductType());
        row.put("unitsRequested", request.getUnitsRequested());
        row.put("urgency", request.getUrgency());
        row.put("status", request.getStatus());
        row.put("supplierBloodBankId", request.getSupplierBloodBankId());
        row.put("supplierBloodBankName", request.getSupplierBloodBankName());
        row.put("requiredBy", request.getRequiredBy());
        row.put("reason", request.getReason());
        row.put("currentUnits", request.getCurrentUnits());
        row.put("capacity", request.getCapacity());
        row.put("requestedById", request.getRequestedById());
        row.put("requestedByName", request.getRequestedByName());
        row.put("followUpNotes", request.getFollowUpNotes() != null ? request.getFollowUpNotes() : List.of());
        row.put("createdAt", request.getCreatedAt());
        row.put("updatedAt", request.getUpdatedAt());
        return row;
    }

    private void authorizeAccess() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Only staff and admin can access supply requests", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return role != null && ("staff".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role));
    }

    private BloodGroup parseBloodGroup(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return BloodGroup.fromValue(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BAD_REQUEST", "Invalid blood group: " + value, HttpStatus.BAD_REQUEST);
        }
    }

    private BloodProductType parseBloodProductType(Object value) {
        String raw = requireString(value, "bloodProductType");
        try {
            return BloodProductType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            try {
                return BloodProductType.fromValue(raw);
            } catch (IllegalArgumentException inner) {
                throw new ApiException("BAD_REQUEST", "Invalid blood product type: " + raw, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private Urgency parseUrgency(Object value) {
        String raw = requireString(value, "urgency");
        try {
            return Urgency.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BAD_REQUEST", "Invalid urgency: " + raw, HttpStatus.BAD_REQUEST);
        }
    }

    private Integer parseInteger(Object value, String field) {
        if (value == null) {
            throw new ApiException("BAD_REQUEST", field + " is required", HttpStatus.BAD_REQUEST);
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed < 1) {
                throw new ApiException("BAD_REQUEST", field + " must be at least 1", HttpStatus.BAD_REQUEST);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ApiException("BAD_REQUEST", "Invalid " + field, HttpStatus.BAD_REQUEST);
        }
    }

    private Integer parseOptionalInteger(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private LocalDate parseDate(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ApiException("BAD_REQUEST", field + " is required", HttpStatus.BAD_REQUEST);
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private UUID parseUuid(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ApiException("BAD_REQUEST", field + " is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("BAD_REQUEST", "Invalid " + field, HttpStatus.BAD_REQUEST);
        }
    }

    private String requireString(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ApiException("BAD_REQUEST", field + " is required", HttpStatus.BAD_REQUEST);
        }
        return String.valueOf(value).trim();
    }
}
