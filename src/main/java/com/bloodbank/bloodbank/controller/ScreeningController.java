package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.CompleteScreeningRequest;
import com.bloodbank.bloodbank.entity.Appointment;
import com.bloodbank.bloodbank.entity.ScreeningRecord;
import com.bloodbank.bloodbank.service.AppointmentService;
import com.bloodbank.bloodbank.service.ScreeningService;
import com.bloodbank.bloodbank.service.SpecialistPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/screening")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;
    private final SpecialistPortalService specialistPortalService;
    private final AppointmentService appointmentService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) UUID donorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ControllerUtils.paged(screeningService.listScreenings(donorId, page, limit, sort));
    }

    @GetMapping("/records")
    public ApiResponse<?> listRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) UUID donorId) {
        return ControllerUtils.paged(screeningService.listScreenings(donorId, page, limit, sort));
    }

    @GetMapping("/export")
    public ApiResponse<List<ScreeningRecord>> export(
            @RequestParam(defaultValue = "csv") String format) {
        return ApiResponse.ok(Collections.emptyList());
    }

    @GetMapping("/schedules")
    public ApiResponse<?> getSchedules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ControllerUtils.paged(specialistPortalService.getSchedules(page, limit));
    }

    @GetMapping("/records/{id}")
    public ApiResponse<ScreeningRecord> getRecordById(@PathVariable UUID id) {
        return ApiResponse.ok(screeningService.getById(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScreeningRecord> getById(@PathVariable UUID id) {
        return ApiResponse.ok(screeningService.getById(id));
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ScreeningRecord>> createSession(@Valid @RequestBody ScreeningRecord screening) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(screeningService.createScreening(screening)));
    }

    @GetMapping("/sessions/active")
    public ApiResponse<ScreeningRecord> getActiveSession(@RequestParam UUID donorId) {
        return ApiResponse.ok(screeningService.getActiveSessionForDonor(donorId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScreeningRecord>> create(@Valid @RequestBody ScreeningRecord screening) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(screeningService.createScreening(screening)));
    }

    @PatchMapping("/sessions/{id}")
    public ApiResponse<ScreeningRecord> updateSession(@PathVariable UUID id, @Valid @RequestBody ScreeningRecord updates) {
        return ApiResponse.ok(screeningService.updateScreening(id, updates));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ScreeningRecord> update(@PathVariable UUID id, @Valid @RequestBody ScreeningRecord updates) {
        return ApiResponse.ok(screeningService.updateScreening(id, updates));
    }

    @PostMapping("/sessions/{id}/complete")
    public ApiResponse<ScreeningRecord> completeSession(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteScreeningRequest request) {
        return ApiResponse.ok(screeningService.completeScreening(
                id,
                request.getEligibilityResult(),
                request.getDeferralReason(),
                request.getDeferralUntil(),
                request.getNotes(),
                request.getBloodGroup()));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<ScreeningRecord> complete(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteScreeningRequest request) {
        return ApiResponse.ok(screeningService.completeScreening(
                id,
                request.getEligibilityResult(),
                request.getDeferralReason(),
                request.getDeferralUntil(),
                request.getNotes(),
                request.getBloodGroup()));
    }

    @PostMapping("/schedules/{appointmentId}/complete")
    public ApiResponse<Appointment> completeSchedule(@PathVariable UUID appointmentId) {
        return ApiResponse.ok(appointmentService.completeScheduledAppointment(appointmentId));
    }
}
