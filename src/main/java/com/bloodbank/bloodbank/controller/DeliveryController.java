package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.ConfirmDeliveryRequest;
import com.bloodbank.bloodbank.entity.Delivery;
import com.bloodbank.bloodbank.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID hospitalId) {
        return ControllerUtils.paged(deliveryService.listDeliveries(hospitalId, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Delivery> getById(@PathVariable UUID id) {
        return ApiResponse.ok(deliveryService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Delivery>> create(@Valid @RequestBody Delivery delivery) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(deliveryService.scheduleDelivery(delivery)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Delivery> update(@PathVariable UUID id, @Valid @RequestBody Delivery updates) {
        return ApiResponse.ok(deliveryService.updateDelivery(id, updates));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Delivery> confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmDeliveryRequest request) {
        return ApiResponse.ok(deliveryService.confirmReceipt(id, request.getReceivedBy()));
    }
}
