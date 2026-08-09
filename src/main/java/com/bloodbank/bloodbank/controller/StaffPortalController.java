package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.MarkPaidRequest;
import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.service.DonorService;
import com.bloodbank.bloodbank.service.StaffPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffPortalController {

    private final StaffPortalService staffPortalService;
    private final DonorService donorService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(staffPortalService.getDashboard());
    }

    @GetMapping("/donors")
    public ApiResponse<?> searchDonors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DonorStatus status) {
        return ControllerUtils.paged(donorService.listDonors(search, status, page, limit, sort));
    }

    @PostMapping("/supply-requests")
    public ApiResponse<Map<String, Object>> createSupplyRequest(@Valid @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(Map.of("status", "submitted", "request", request));
    }

    @GetMapping("/payments")
    public ApiResponse<?> listPayments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) PaymentStatus status) {
        return ControllerUtils.paged(staffPortalService.listPayments(status, page, limit));
    }

    @PatchMapping("/payments/{id}/mark-paid")
    public ApiResponse<CompensationPayment> markPaymentPaid(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) MarkPaidRequest request) {
        var method = request != null ? request.getMethod() : null;
        return ApiResponse.ok(staffPortalService.markPaymentPaid(id, method));
    }
}
