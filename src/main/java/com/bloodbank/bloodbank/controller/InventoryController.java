package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.DiscardUnitRequest;
import com.bloodbank.bloodbank.dto.request.ReserveUnitRequest;
import com.bloodbank.bloodbank.entity.BloodUnit;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import com.bloodbank.bloodbank.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/summary")
    public ApiResponse<List<Map<String, Object>>> getSummary() {
        return ApiResponse.ok(inventoryService.getSummary());
    }

    @GetMapping("/units")
    public ApiResponse<?> listUnits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UnitStatus status) {
        return ControllerUtils.paged(inventoryService.listUnits(status, page, limit, sort));
    }

    @GetMapping("/units/{id}")
    public ApiResponse<BloodUnit> getUnit(@PathVariable String id) {
        return ApiResponse.ok(inventoryService.getUnitById(id));
    }

    @PatchMapping("/units/{id}")
    public ApiResponse<BloodUnit> updateUnit(@PathVariable String id, @Valid @RequestBody BloodUnit updates) {
        return ApiResponse.ok(inventoryService.updateUnit(id, updates));
    }

    @PostMapping("/units/{id}/reserve")
    public ApiResponse<BloodUnit> reserve(@PathVariable String id, @Valid @RequestBody ReserveUnitRequest request) {
        return ApiResponse.ok(inventoryService.reserveUnit(id, request.getRequestId()));
    }

    @PostMapping("/units/{id}/release")
    public ApiResponse<BloodUnit> release(@PathVariable String id) {
        return ApiResponse.ok(inventoryService.releaseUnit(id));
    }

    @PostMapping("/units/{id}/issue")
    public ApiResponse<BloodUnit> issue(@PathVariable String id) {
        return ApiResponse.ok(inventoryService.issueUnit(id));
    }

    @PostMapping("/units/{id}/discard")
    public ApiResponse<BloodUnit> discard(@PathVariable String id, @Valid @RequestBody DiscardUnitRequest request) {
        return ApiResponse.ok(inventoryService.discardUnit(id, request.getReason()));
    }

    @GetMapping("/expiring")
    public ApiResponse<List<BloodUnit>> getExpiring(@RequestParam(defaultValue = "7") int withinDays) {
        return ApiResponse.ok(inventoryService.getExpiringUnits(withinDays));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<Map<String, Object>>> getAlerts() {
        return ApiResponse.ok(inventoryService.getAlerts());
    }
}
