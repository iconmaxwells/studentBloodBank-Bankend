package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.PayCompensationRequest;
import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.service.CompensationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compensations")
@RequiredArgsConstructor
public class CompensationController {

    private final CompensationService compensationService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) PaymentStatus status) {
        return ControllerUtils.paged(compensationService.listCompensations(status, page, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<CompensationPayment> getById(@PathVariable UUID id) {
        return ApiResponse.ok(compensationService.getById(id));
    }

    @PostMapping("/{id}/pay")
    public ApiResponse<Map<String, Object>> pay(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) PayCompensationRequest request) {
        return ApiResponse.ok(compensationService.pay(id, request));
    }
}
