package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.ActivityLog;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.repository.ActivityLogRepository;
import com.bloodbank.bloodbank.repository.ActivityLogSpecifications;
import com.bloodbank.bloodbank.util.ExportUtils;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityLogQueryService {

    private final ActivityLogRepository activityLogRepository;

    public Map<String, Object> list(int page, int limit, String sort, String search, String category,
                                    UUID staffId, LocalDate from, LocalDate to) {
        requireAdmin();
        String effectiveSort = (sort == null || sort.isBlank()) ? "-timestamp" : sort;
        PageRequest pageable = PageUtils.toPageRequest(page, limit, effectiveSort);
        Specification<ActivityLog> spec = ActivityLogSpecifications.withFilters(category, staffId, from, to, search);
        Page<ActivityLog> result = activityLogRepository.findAll(spec, pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    public ActivityLog getById(UUID id) {
        requireAdmin();
        return activityLogRepository.findById(id)
                .orElseThrow(() -> new com.bloodbank.bloodbank.exception.ResourceNotFoundException("Activity log"));
    }

    public byte[] export(String format, String category, UUID staffId, LocalDate from, LocalDate to) {
        requireAdmin();
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = to != null ? to.atTime(LocalTime.MAX) : LocalDateTime.now();
        Specification<ActivityLog> spec = ActivityLogSpecifications.withFilters(category, staffId, from, to, null)
                .and((root, query, cb) -> cb.between(root.get("timestamp"), start, end));
        List<ActivityLog> logs = activityLogRepository.findAll(spec, PageRequest.of(0, 10_000)).getContent();

        List<String> headers = List.of("timestamp", "action", "actionType", "description", "category", "staffName");
        List<List<String>> rows = logs.stream()
                .map(log -> List.of(
                        log.getTimestamp() != null ? log.getTimestamp().toString() : "",
                        log.getAction() != null ? log.getAction() : "",
                        log.getActionType() != null ? log.getActionType().name() : "",
                        log.getDescription() != null ? log.getDescription() : "",
                        log.getCategory() != null ? log.getCategory() : "",
                        log.getStaffName() != null ? log.getStaffName() : ""
                ))
                .toList();

        if ("pdf".equalsIgnoreCase(format)) {
            try {
                return ExportUtils.toPdf("Activity Logs", headers, rows);
            } catch (Exception e) {
                throw new ApiException("PDF_ERROR", "Failed to export PDF", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return ExportUtils.toCsv(headers, rows);
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }
}
