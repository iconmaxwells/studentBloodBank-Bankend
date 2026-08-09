package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.HospitalCreateRequest;
import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalStatus;
import com.bloodbank.bloodbank.service.BloodRequestService;
import com.bloodbank.bloodbank.service.HospitalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hospitals")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalService hospitalService;
    private final BloodRequestService bloodRequestService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) HospitalStatus status) {
        return ControllerUtils.paged(hospitalService.listHospitals(search, status, page, limit, sort));
    }

    @GetMapping("/nearby")
    public ApiResponse<List<Map<String, Object>>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "25") double radiusKm) {
        return ApiResponse.ok(hospitalService.findNearby(lat, lng, radiusKm));
    }

    @GetMapping("/{id}")
    public ApiResponse<Hospital> getById(@PathVariable String id) {
        if (ControllerUtils.isUuid(id)) {
            return ApiResponse.ok(hospitalService.getById(ControllerUtils.toUuid(id)));
        }
        return ApiResponse.ok(hospitalService.getByDisplayCode(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Hospital>> create(@Valid @RequestBody HospitalCreateRequest request) {
        Hospital created = hospitalService.createHospital(
                request.toHospital(), request.getEmail(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Hospital> update(@PathVariable String id, @Valid @RequestBody Hospital updates) {
        return ApiResponse.ok(hospitalService.updateHospital(resolveHospitalId(id), updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String id) {
        hospitalService.deleteHospital(resolveHospitalId(id));
        return ApiResponse.ok(Map.of("message", "Hospital deleted successfully"));
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> getStats(@PathVariable String id) {
        return ApiResponse.ok(hospitalService.getStats(resolveHospitalId(id)));
    }

    @GetMapping("/{id}/requests")
    public ApiResponse<?> getRequests(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus status) {
        return ControllerUtils.paged(
                bloodRequestService.listRequests(status, resolveHospitalId(id), page, limit, sort));
    }

    private UUID resolveHospitalId(String id) {
        if (ControllerUtils.isUuid(id)) {
            return hospitalService.getById(ControllerUtils.toUuid(id)).getId();
        }
        return hospitalService.getByDisplayCode(id).getId();
    }
}
