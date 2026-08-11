package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.Transaction;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TransactionType;
import com.bloodbank.bloodbank.service.FinanceService;
import com.bloodbank.bloodbank.service.HospitalBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finances")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final HospitalBillingService hospitalBillingService;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary() {
        return ApiResponse.ok(financeService.getSummary());
    }

    @GetMapping("/transactions")
    public ApiResponse<?> listTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type) {
        return ControllerUtils.paged(financeService.listTransactions(type, page, limit, sort));
    }

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<Transaction>> createTransaction(@Valid @RequestBody Transaction transaction) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(financeService.createTransaction(transaction)));
    }

    @GetMapping("/charts/monthly")
    public ApiResponse<Map<String, Object>> getMonthlyChart(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(financeService.getMonthlyChartData(months));
    }

    @GetMapping("/reports/monthly")
    public ApiResponse<Map<String, Object>> getMonthlyReport(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(financeService.getMonthlyChartData(months));
    }

    @GetMapping("/charts/breakdown")
    public ApiResponse<Map<String, Object>> getBreakdownChart() {
        return ApiResponse.ok(financeService.getBreakdownChart());
    }

    @GetMapping("/export")
    public ApiResponse<List<Transaction>> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type) {
        return ApiResponse.ok(Collections.emptyList());
    }

    @GetMapping("/hospital-charges")
    public ApiResponse<?> listHospitalCharges(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) UUID hospitalId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String billingPeriod) {
        return ControllerUtils.paged(hospitalBillingService.listCharges(hospitalId, status, billingPeriod, page, limit, sort));
    }

    @PostMapping("/hospital-charges/generate")
    public ApiResponse<Map<String, Object>> generateHospitalCharges(
            @RequestParam(required = false) String billingPeriod) {
        YearMonth period = billingPeriod != null && !billingPeriod.isBlank()
                ? YearMonth.parse(billingPeriod)
                : YearMonth.now();
        return ApiResponse.ok(hospitalBillingService.generateMonthlyCharges(period));
    }

    @PostMapping("/hospital-charges/{id}/mark-paid")
    public ApiResponse<Map<String, Object>> markHospitalChargePaid(@PathVariable UUID id) {
        return ApiResponse.ok(hospitalBillingService.markChargePaid(id));
    }
}
