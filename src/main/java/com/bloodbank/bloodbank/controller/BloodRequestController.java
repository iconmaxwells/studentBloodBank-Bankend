package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.RejectReasonRequest;
import com.bloodbank.bloodbank.entity.BloodRequest;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import com.bloodbank.bloodbank.service.BloodRequestService;
import com.bloodbank.bloodbank.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class BloodRequestController {

    private final BloodRequestService bloodRequestService;
    private final ReportService reportService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) UUID hospitalId) {
        return ControllerUtils.paged(bloodRequestService.listRequests(status, hospitalId, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<BloodRequest> getById(@PathVariable UUID id) {
        return ApiResponse.ok(bloodRequestService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BloodRequest>> create(@Valid @RequestBody BloodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bloodRequestService.createRequest(request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BloodRequest> update(@PathVariable UUID id, @Valid @RequestBody BloodRequest updates) {
        return ApiResponse.ok(bloodRequestService.updateRequest(id, updates));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<BloodRequest> approve(@PathVariable UUID id) {
        return ApiResponse.ok(bloodRequestService.approveRequest(id));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<BloodRequest> reject(@PathVariable UUID id, @Valid @RequestBody RejectReasonRequest request) {
        return ApiResponse.ok(bloodRequestService.rejectRequest(id, request.getReason()));
    }

    @PostMapping("/{id}/process")
    public ApiResponse<BloodRequest> process(@PathVariable UUID id) {
        return ApiResponse.ok(bloodRequestService.processRequest(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<BloodRequest> complete(@PathVariable UUID id) {
        return ApiResponse.ok(bloodRequestService.completeRequest(id));
    }

    @GetMapping("/{id}/progress")
    public ApiResponse<Map<String, Object>> getProgress(@PathVariable UUID id) {
        BloodRequest request = bloodRequestService.getById(id);
        return ApiResponse.ok(Map.of(
                "requestId", request.getId(),
                "displayCode", request.getDisplayCode(),
                "status", request.getStatus(),
                "timeline", List.of(Map.of("status", request.getStatus(), "timestamp", request.getUpdatedAt()))
        ));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> getReceipt(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "json") String format) {
        BloodRequest request = bloodRequestService.getById(id);
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdf = reportService.getReceiptPdf(request);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"receipt-" + request.getDisplayCode() + ".pdf\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(("{\"success\":true,\"data\":{\"receiptNumber\":\"" + request.getDisplayCode()
                        + "\",\"status\":\"" + request.getStatus() + "\"}}").getBytes());
    }

    @GetMapping("/{id}/label")
    public ResponseEntity<byte[]> getLabel(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "pdf") String format) {
        BloodRequest request = bloodRequestService.getById(id);
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdf = reportService.getLabelPdf(request);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"label-" + request.getDisplayCode() + ".pdf\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(("{\"success\":true,\"data\":{\"labelCode\":\"" + request.getDisplayCode()
                        + "\",\"urgency\":\"" + request.getUrgency() + "\"}}").getBytes());
    }
}
