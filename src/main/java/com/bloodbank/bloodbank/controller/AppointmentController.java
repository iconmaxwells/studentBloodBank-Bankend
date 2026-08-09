package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.RejectReasonRequest;
import com.bloodbank.bloodbank.entity.Appointment;
import com.bloodbank.bloodbank.entity.AppointmentRequest;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentRequestStatus;
import com.bloodbank.bloodbank.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping("/api/v1/appointments")
    public ApiResponse<?> listAppointments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID donorId) {
        return ControllerUtils.paged(appointmentService.listAppointments(donorId, page, limit, sort));
    }

    @GetMapping("/api/v1/appointments/{id}")
    public ApiResponse<Appointment> getAppointment(@PathVariable UUID id) {
        return ApiResponse.ok(appointmentService.getById(id));
    }

    @PostMapping("/api/v1/appointments")
    public ResponseEntity<ApiResponse<Appointment>> createAppointment(@Valid @RequestBody Appointment appointment) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(appointmentService.createAppointment(appointment)));
    }

    @PatchMapping("/api/v1/appointments/{id}")
    public ApiResponse<Appointment> updateAppointment(@PathVariable UUID id, @Valid @RequestBody Appointment updates) {
        return ApiResponse.ok(appointmentService.updateAppointment(id, updates));
    }

    @PostMapping("/api/v1/appointments/{id}/cancel")
    public ApiResponse<Appointment> cancelAppointment(@PathVariable UUID id) {
        return ApiResponse.ok(appointmentService.cancelAppointment(id));
    }

    @PostMapping("/api/v1/appointments/{id}/confirm")
    public ApiResponse<Appointment> confirmAppointment(@PathVariable UUID id) {
        return ApiResponse.ok(appointmentService.confirmAppointment(id));
    }

    @GetMapping("/api/v1/appointment-requests")
    public ApiResponse<?> listAppointmentRequests(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) AppointmentRequestStatus status) {
        return ControllerUtils.paged(appointmentService.listAppointmentRequests(status, page, limit));
    }

    @PostMapping("/api/v1/appointment-requests/{id}/approve")
    public ApiResponse<Appointment> approveAppointmentRequest(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) Map<String, String> body) {
        String staffResponse = body != null ? body.get("staffResponse") : null;
        return ApiResponse.ok(appointmentService.approveAppointmentRequest(id, staffResponse));
    }

    @PostMapping("/api/v1/appointment-requests/{id}/reject")
    public ApiResponse<AppointmentRequest> rejectAppointmentRequest(
            @PathVariable UUID id,
            @Valid @RequestBody RejectReasonRequest request) {
        return ApiResponse.ok(appointmentService.rejectAppointmentRequest(id, request.getReason()));
    }
}
