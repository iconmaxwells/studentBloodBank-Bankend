package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.ReportJob;
import com.bloodbank.bloodbank.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/collections")
    public ApiResponse<List<Map<String, Object>>> collections(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.collectionsReport(from, to));
    }

    @GetMapping("/inventory")
    public ApiResponse<List<Map<String, Object>>> inventory() {
        return ApiResponse.ok(reportService.inventoryReport());
    }

    @GetMapping("/requests")
    public ApiResponse<List<Map<String, Object>>> requests(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.requestsReport(from, to));
    }

    @GetMapping("/donors")
    public ApiResponse<List<Map<String, Object>>> donors() {
        return ApiResponse.ok(reportService.donorsReport());
    }

    @GetMapping("/custom")
    public ApiResponse<Map<String, Object>> custom(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(reportService.customReport(from, to, type));
    }

    @PostMapping("/generate")
    public ApiResponse<ReportJob> generate(@Valid @RequestBody Map<String, Object> request) {
        return ApiResponse.ok(reportService.generateAsync(request));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<Map<String, Object>> getJob(@PathVariable UUID id) {
        ReportJob job = reportService.getJob(id);
        return ApiResponse.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus(),
                "type", job.getType(),
                "format", job.getFormat(),
                "fileName", job.getFileName() != null ? job.getFileName() : "",
                "downloadUrl", "/api/v1/reports/jobs/" + job.getId() + "/download",
                "errorMessage", job.getErrorMessage() != null ? job.getErrorMessage() : ""
        ));
    }

    @GetMapping("/jobs/{id}/download")
    public ResponseEntity<byte[]> downloadJob(@PathVariable UUID id) {
        ReportJob job = reportService.getJob(id);
        if (!"completed".equals(job.getStatus()) || job.getContent() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(job.getContentType()))
                .body(job.getContent());
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(reportService.getDashboard());
    }

    @GetMapping("/collections/monthly")
    public ApiResponse<Map<String, Object>> collectionsMonthly() {
        return ApiResponse.ok(reportService.getCollectionsMonthly());
    }

    @GetMapping("/inventory/distribution")
    public ApiResponse<List<Map<String, Object>>> inventoryDistribution() {
        return ApiResponse.ok(reportService.getInventoryDistribution());
    }

    @GetMapping("/requests/by-urgency")
    public ApiResponse<List<Map<String, Object>>> requestsByUrgency(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(reportService.getRequestsByUrgency(from, to));
    }

    @GetMapping("/demand-prediction")
    public ApiResponse<Map<String, Object>> demandPrediction() {
        return ApiResponse.ok(reportService.getDemandPrediction());
    }

    @GetMapping("/live-monitoring")
    public ApiResponse<Map<String, Object>> liveMonitoring() {
        return ApiResponse.ok(reportService.getLiveMonitoring());
    }
}
