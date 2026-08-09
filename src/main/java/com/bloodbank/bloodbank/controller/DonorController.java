package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.DonorCreateRequest;
import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.service.DonorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/donors")
@RequiredArgsConstructor
public class DonorController {

    private final DonorService donorService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DonorStatus status) {
        return ControllerUtils.paged(donorService.listDonors(search, status, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Donor> getById(@PathVariable String id) {
        if (ControllerUtils.isUuid(id)) {
            return ApiResponse.ok(donorService.getById(ControllerUtils.toUuid(id)));
        }
        return ApiResponse.ok(donorService.getByDisplayCode(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Donor>> create(@Valid @RequestBody DonorCreateRequest request) {
        Donor created = donorService.createDonor(request.toDonor(), request.getEmail(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Donor> update(@PathVariable String id, @Valid @RequestBody Donor updates) {
        UUID donorId = resolveDonorId(id);
        return ApiResponse.ok(donorService.updateDonor(donorId, updates));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable String id) {
        donorService.deleteDonor(resolveDonorId(id));
        return ApiResponse.ok(Map.of("message", "Donor deleted successfully"));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<?> getHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ControllerUtils.paged(donorService.getDonationHistory(resolveDonorId(id), page, limit));
    }

    @GetMapping("/{id}/eligibility")
    public ApiResponse<Map<String, Object>> getEligibility(@PathVariable String id) {
        return ApiResponse.ok(donorService.checkEligibility(resolveDonorId(id)));
    }

    @GetMapping("/{id}/collections")
    public ApiResponse<?> getCollections(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ControllerUtils.paged(donorService.getDonationHistory(resolveDonorId(id), page, limit));
    }

    private UUID resolveDonorId(String id) {
        if (ControllerUtils.isUuid(id)) {
            return donorService.getById(ControllerUtils.toUuid(id)).getId();
        }
        return donorService.getByDisplayCode(id).getId();
    }
}
