package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.BloodBank;
import com.bloodbank.bloodbank.service.BloodBankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blood-banks")
@RequiredArgsConstructor
public class BloodBankController {

    private final BloodBankService bloodBankService;

    @GetMapping
    public ApiResponse<List<BloodBank>> list() {
        return ApiResponse.ok(bloodBankService.listBloodBanks());
    }

    @GetMapping("/nearby")
    public ApiResponse<List<Map<String, Object>>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "25") double radiusKm) {
        return ApiResponse.ok(bloodBankService.findNearby(lat, lng, radiusKm));
    }

    @GetMapping("/{id}")
    public ApiResponse<BloodBank> getById(@PathVariable String id) {
        if (ControllerUtils.isUuid(id)) {
            return ApiResponse.ok(bloodBankService.getById(ControllerUtils.toUuid(id)));
        }
        return ApiResponse.ok(bloodBankService.getByDisplayCode(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BloodBank>> create(@Valid @RequestBody BloodBank bloodBank) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(bloodBankService.createBloodBank(bloodBank)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BloodBank> update(@PathVariable UUID id, @Valid @RequestBody BloodBank updates) {
        return ApiResponse.ok(bloodBankService.updateBloodBank(id, updates));
    }
}
