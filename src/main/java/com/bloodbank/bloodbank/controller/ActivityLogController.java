package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.ActivityLog;
import com.bloodbank.bloodbank.service.ActivityLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activity-logs")
@RequiredArgsConstructor
public class ActivityLogController {

    private final ActivityLogQueryService activityLogQueryService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<String, Object> result = activityLogQueryService.list(page, limit, sort, search, category, staffId, from, to);
        return ControllerUtils.paged(result);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] content = activityLogQueryService.export(format, category, staffId, from, to);
        String contentType = "pdf".equalsIgnoreCase(format) ? "application/pdf" : "text/csv";
        String extension = "pdf".equalsIgnoreCase(format) ? "pdf" : "csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"activity-logs." + extension + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(content);
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityLog> getById(@PathVariable UUID id) {
        return ApiResponse.ok(activityLogQueryService.getById(id));
    }
}
