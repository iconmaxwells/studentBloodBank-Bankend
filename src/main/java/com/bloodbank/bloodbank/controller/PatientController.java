package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.Patient;
import com.bloodbank.bloodbank.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID hospitalId) {
        return ControllerUtils.paged(patientService.listPatients(hospitalId, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Patient> getById(@PathVariable UUID id) {
        return ApiResponse.ok(patientService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Patient>> create(@Valid @RequestBody Patient patient) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(patientService.createPatient(patient)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Patient> update(@PathVariable UUID id, @Valid @RequestBody Patient updates) {
        return ApiResponse.ok(patientService.updatePatient(id, updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable UUID id) {
        patientService.deletePatient(id);
        return ApiResponse.ok(Map.of("message", "Patient deleted successfully"));
    }
}
