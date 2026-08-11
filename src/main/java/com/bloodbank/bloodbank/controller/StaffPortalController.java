package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.MarkPaidRequest;
import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.service.DonorService;
import com.bloodbank.bloodbank.service.StaffPortalService;
import com.bloodbank.bloodbank.service.SupplyRequestService;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.SupplyRequestStatus;
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
    private final SupplyRequestService supplyRequestService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(staffPortalService.getDashboard());
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> getProfile() {
        return ApiResponse.ok(staffPortalService.getProfile());
    }

    @PatchMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(staffPortalService.updateProfile(body));
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
        return ApiResponse.ok(supplyRequestService.createRequest(request));
    }

    @GetMapping("/supply-requests")
    public ApiResponse<?> listSupplyRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) SupplyRequestStatus status) {
        return ControllerUtils.paged(supplyRequestService.listRequests(status, page, limit, sort));
    }

    @GetMapping("/supply-requests/{id}")
    public ApiResponse<Map<String, Object>> getSupplyRequest(@PathVariable UUID id) {
        return ApiResponse.ok(supplyRequestService.getRequest(id));
    }

    @PatchMapping("/supply-requests/{id}/status")
    public ApiResponse<Map<String, Object>> updateSupplyRequestStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        SupplyRequestStatus status = body.get("status") != null
                ? SupplyRequestStatus.valueOf(String.valueOf(body.get("status")))
                : null;
        String note = body.get("note") != null ? String.valueOf(body.get("note")) : null;
        return ApiResponse.ok(supplyRequestService.updateStatus(id, status, note));
    }

    @PostMapping("/supply-requests/{id}/follow-up")
    public ApiResponse<Map<String, Object>> addSupplyRequestFollowUp(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String note = body.get("note") != null ? String.valueOf(body.get("note")) : null;
        return ApiResponse.ok(supplyRequestService.addFollowUp(id, note));
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
