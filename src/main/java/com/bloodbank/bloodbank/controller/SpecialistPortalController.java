package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.ScreeningRecord;
import com.bloodbank.bloodbank.service.SpecialistPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/specialist")
@RequiredArgsConstructor
public class SpecialistPortalController {

    private final SpecialistPortalService specialistPortalService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.ok(specialistPortalService.getDashboard());
    }

    @GetMapping("/schedules")
    public ApiResponse<?> getSchedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String date) {
        return ControllerUtils.paged(specialistPortalService.getSchedules(page, limit));
    }

    @GetMapping("/records")
    public ApiResponse<?> getRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search) {
        return ControllerUtils.paged(specialistPortalService.getRecords(page, limit));
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ScreeningRecord>> startSession(@Valid @RequestBody ScreeningRecord session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(specialistPortalService.startSession(session)));
    }

    @PatchMapping("/sessions/{id}")
    public ApiResponse<ScreeningRecord> updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody ScreeningRecord updates) {
        return ApiResponse.ok(specialistPortalService.updateSession(id, updates));
    }
}
