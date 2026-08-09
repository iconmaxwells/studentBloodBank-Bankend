package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.entity.Collection;
import com.bloodbank.bloodbank.entity.CollectionSession;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CollectionStatus;
import com.bloodbank.bloodbank.service.CollectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CollectionStatus status) {
        return ControllerUtils.paged(collectionService.listCollections(status, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<Collection> getById(@PathVariable UUID id) {
        return ApiResponse.ok(collectionService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Collection>> create(@Valid @RequestBody Collection collection) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(collectionService.createCollection(collection)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Collection> update(@PathVariable UUID id, @Valid @RequestBody Collection updates) {
        return ApiResponse.ok(collectionService.updateCollection(id, updates));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        return ApiResponse.ok(collectionService.getStats());
    }

    @GetMapping("/sessions")
    public ApiResponse<?> listSessions(
            @RequestParam(required = false) UUID staffId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ControllerUtils.paged(collectionService.listSessions(staffId, page, limit));
    }

    @GetMapping("/sessions/active")
    public ApiResponse<CollectionSession> getActiveSession() {
        return ApiResponse.ok(collectionService.getActiveSession());
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<CollectionSession>> startSession(@Valid @RequestBody CollectionSession session) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(collectionService.startSession(session)));
    }

    @PatchMapping("/sessions/{id}")
    public ApiResponse<CollectionSession> updateSession(
            @PathVariable UUID id,
            @Valid @RequestBody CollectionSession updates) {
        return ApiResponse.ok(collectionService.updateSession(id, updates));
    }
}
