package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.ExportUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final CollectionRepository collectionRepository;
    private final BloodUnitRepository bloodUnitRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final DonorRepository donorRepository;
    private final ReportJobRepository reportJobRepository;
    private final InventoryService inventoryService;
    private final AdminDashboardService adminDashboardService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> collectionsReport(LocalDate from, LocalDate to) {
        requireAdmin();
        return collectionRepository.findAll().stream()
                .filter(c -> inRange(c.getCollectionDate(), from, to))
                .map(c -> Map.<String, Object>of(
                        "displayCode", c.getDisplayCode(),
                        "donorId", c.getDonorId(),
                        "bloodGroup", c.getBloodGroup() != null ? c.getBloodGroup().getValue() : "",
                        "productType", c.getBloodProductType() != null ? c.getBloodProductType().getValue() : "",
                        "volumeMl", c.getVolumeMl(),
                        "status", c.getStatus(),
                        "collectionDate", c.getCollectionDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> inventoryReport() {
        requireAdmin();
        return inventoryService.getSummary();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> requestsReport(LocalDate from, LocalDate to) {
        requireAdmin();
        return bloodRequestRepository.findAll().stream()
                .filter(r -> inRange(r.getRequestDate(), from, to))
                .map(r -> Map.<String, Object>of(
                        "displayCode", r.getDisplayCode(),
                        "hospitalId", r.getHospitalId(),
                        "bloodGroup", r.getBloodGroup() != null ? r.getBloodGroup().getValue() : "",
                        "productType", r.getBloodProductType() != null ? r.getBloodProductType().getValue() : "",
                        "unitsRequested", r.getUnitsRequested(),
                        "unitsFulfilled", r.getUnitsFulfilled(),
                        "urgency", r.getUrgency(),
                        "status", r.getStatus(),
                        "requestDate", r.getRequestDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> donorsReport() {
        requireAdmin();
        return donorRepository.findAll().stream()
                .map(d -> Map.<String, Object>of(
                        "displayCode", d.getDisplayCode(),
                        "name", d.getFirstName() + " " + d.getLastName(),
                        "bloodGroup", d.getBloodGroup() != null ? d.getBloodGroup().getValue() : "",
                        "status", d.getStatus(),
                        "totalDonations", d.getTotalDonations(),
                        "registeredDate", d.getRegisteredDate()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> customReport(LocalDate from, LocalDate to, String type) {
        requireAdmin();
        return switch (type != null ? type : "collections") {
            case "inventory" -> Map.of("type", "inventory", "data", inventoryReport());
            case "requests" -> Map.of("type", "requests", "data", requestsReport(from, to));
            case "donors" -> Map.of("type", "donors", "data", donorsReport());
            default -> Map.of("type", "collections", "data", collectionsReport(from, to));
        };
    }

    public ReportJob generateAsync(Map<String, Object> request) {
        requireAdmin();
        String type = (String) request.getOrDefault("type", "collections");
        String format = (String) request.getOrDefault("format", "csv");
        ReportJob job = reportJobRepository.save(ReportJob.builder()
                .type(type)
                .format(format)
                .status("pending")
                .build());
        processJobAsync(job.getId(), request);
        return job;
    }

    @Async
    public void processJobAsync(UUID jobId, Map<String, Object> request) {
        try {
            ReportJob job = reportJobRepository.findById(jobId).orElseThrow();
            String type = job.getType();
            String format = job.getFormat();
            LocalDate from = parseDate(request.get("from"));
            LocalDate to = parseDate(request.get("to"));

            List<String> headers;
            List<List<String>> rows;
            switch (type) {
                case "inventory" -> {
                    headers = List.of("bloodGroup", "bloodProductType", "availableUnits", "trend");
                    rows = inventoryReport().stream()
                            .map(m -> List.of(
                                    ExportUtils.mapValue(m, "bloodGroup"),
                                    ExportUtils.mapValue(m, "bloodProductType"),
                                    ExportUtils.mapValue(m, "availableUnits"),
                                    ExportUtils.mapValue(m, "trend")))
                            .toList();
                }
                case "requests" -> {
                    headers = List.of("displayCode", "bloodGroup", "productType", "unitsRequested", "status", "requestDate");
                    rows = requestsReport(from, to).stream()
                            .map(m -> List.of(
                                    ExportUtils.mapValue(m, "displayCode"),
                                    ExportUtils.mapValue(m, "bloodGroup"),
                                    ExportUtils.mapValue(m, "productType"),
                                    ExportUtils.mapValue(m, "unitsRequested"),
                                    ExportUtils.mapValue(m, "status"),
                                    ExportUtils.mapValue(m, "requestDate")))
                            .toList();
                }
                case "donors" -> {
                    headers = List.of("displayCode", "name", "bloodGroup", "status", "totalDonations");
                    rows = donorsReport().stream()
                            .map(m -> List.of(
                                    ExportUtils.mapValue(m, "displayCode"),
                                    ExportUtils.mapValue(m, "name"),
                                    ExportUtils.mapValue(m, "bloodGroup"),
                                    ExportUtils.mapValue(m, "status"),
                                    ExportUtils.mapValue(m, "totalDonations")))
                            .toList();
                }
                default -> {
                    headers = List.of("displayCode", "bloodGroup", "productType", "volumeMl", "status", "collectionDate");
                    rows = collectionsReport(from, to).stream()
                            .map(m -> List.of(
                                    ExportUtils.mapValue(m, "displayCode"),
                                    ExportUtils.mapValue(m, "bloodGroup"),
                                    ExportUtils.mapValue(m, "productType"),
                                    ExportUtils.mapValue(m, "volumeMl"),
                                    ExportUtils.mapValue(m, "status"),
                                    ExportUtils.mapValue(m, "collectionDate")))
                            .toList();
                }
            }

            byte[] content;
            String contentType;
            String extension;
            if ("pdf".equalsIgnoreCase(format)) {
                content = ExportUtils.toPdf(type + " report", headers, rows);
                contentType = "application/pdf";
                extension = "pdf";
            } else {
                content = ExportUtils.toCsv(headers, rows);
                contentType = "text/csv";
                extension = "csv";
            }

            job.setContent(content);
            job.setContentType(contentType);
            job.setFileName(type + "-report-" + LocalDate.now() + "." + extension);
            job.setStatus("completed");
            job.setCompletedAt(LocalDateTime.now());
            reportJobRepository.save(job);
        } catch (Exception e) {
            reportJobRepository.findById(jobId).ifPresent(job -> {
                job.setStatus("failed");
                job.setErrorMessage(e.getMessage());
                job.setCompletedAt(LocalDateTime.now());
                reportJobRepository.save(job);
            });
        }
    }

    @Transactional(readOnly = true)
    public ReportJob getJob(UUID id) {
        requireAdmin();
        return reportJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report job"));
    }

    @Transactional(readOnly = true)
    public byte[] getReceiptPdf(BloodRequest request) {
        List<String> headers = List.of("Field", "Value");
        List<List<String>> rows = List.of(
                List.of("Receipt Number", request.getDisplayCode()),
                List.of("Hospital ID", request.getHospitalId().toString()),
                List.of("Blood Group", request.getBloodGroup() != null ? request.getBloodGroup().getValue() : ""),
                List.of("Product Type", request.getBloodProductType() != null ? request.getBloodProductType().getValue() : ""),
                List.of("Units Fulfilled", String.valueOf(request.getUnitsFulfilled())),
                List.of("Status", request.getStatus().name()),
                List.of("Completed At", request.getCompletedAt() != null ? request.getCompletedAt().toString() : "")
        );
        try {
            return ExportUtils.toPdf("Blood Request Receipt - " + request.getDisplayCode(), headers, rows);
        } catch (Exception e) {
            throw new ApiException("PDF_ERROR", "Failed to generate receipt", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public byte[] getLabelPdf(BloodRequest request) {
        List<String> headers = List.of("Field", "Value");
        List<List<String>> rows = List.of(
                List.of("Request Code", request.getDisplayCode()),
                List.of("Hospital ID", request.getHospitalId().toString()),
                List.of("Blood Group", request.getBloodGroup() != null ? request.getBloodGroup().getValue() : ""),
                List.of("Product Type", request.getBloodProductType() != null ? request.getBloodProductType().getValue() : ""),
                List.of("Units", String.valueOf(request.getUnitsRequested())),
                List.of("Urgency", request.getUrgency() != null ? request.getUrgency().name() : ""),
                List.of("Patient", request.getPatientName() != null ? request.getPatientName() : ""),
                List.of("Required By", request.getRequiredBy() != null ? request.getRequiredBy().toString() : "")
        );
        try {
            return ExportUtils.toPdf("Shipping Label - " + request.getDisplayCode(), headers, rows);
        } catch (Exception e) {
            throw new ApiException("PDF_ERROR", "Failed to generate label", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        requireAdmin();
        return adminDashboardService.getStats();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCollectionsMonthly() {
        requireAdmin();
        return Map.of("monthlyCollections", adminDashboardService.getCharts().get("monthlyCollections"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getInventoryDistribution() {
        requireAdmin();
        return inventoryReport();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRequestsByUrgency(LocalDate from, LocalDate to) {
        requireAdmin();
        return bloodRequestRepository.findAll().stream()
                .filter(r -> inRange(r.getRequestDate(), from, to))
                .collect(Collectors.groupingBy(
                        r -> r.getUrgency() != null ? r.getUrgency().name() : "Unknown",
                        Collectors.counting()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of("urgency", e.getKey(), "count", e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDemandPrediction() {
        requireAdmin();
        LocalDate from = LocalDate.now().minusMonths(3);
        Map<String, Long> monthlyDemand = bloodRequestRepository.findAll().stream()
                .filter(r -> r.getRequestDate() != null && !r.getRequestDate().isBefore(from))
                .collect(Collectors.groupingBy(
                        r -> r.getRequestDate().getYear() + "-" + String.format("%02d", r.getRequestDate().getMonthValue()),
                        Collectors.summingLong(r -> r.getUnitsRequested() != null ? r.getUnitsRequested() : 0)
                ));
        double avgUnits = monthlyDemand.values().stream().mapToLong(Long::longValue).average().orElse(0);
        return Map.of(
                "historicalMonthlyDemand", monthlyDemand,
                "predictedNextMonthUnits", Math.round(avgUnits * 1.05),
                "confidence", "medium",
                "method", "moving_average"
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLiveMonitoring() {
        requireAdmin();
        return Map.of(
                "inventory", adminDashboardService.getMonitoringInventory(),
                "alerts", adminDashboardService.getMonitoringAlerts(),
                "timestamp", LocalDateTime.now()
        );
    }

    private boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) return true;
        if (from != null && date.isBefore(from)) return false;
        if (to != null && date.isAfter(to)) return false;
        return true;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) return null;
        return LocalDate.parse(value.toString());
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }
}
