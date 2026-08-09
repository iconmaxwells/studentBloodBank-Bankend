package com.bloodbank.bloodbank.controller;

import com.bloodbank.bloodbank.dto.common.ApiResponse;
import com.bloodbank.bloodbank.dto.request.CompleteTestRequest;
import com.bloodbank.bloodbank.dto.request.CreateTestRequest;
import com.bloodbank.bloodbank.dto.request.UpdateTestResultsRequest;
import com.bloodbank.bloodbank.entity.TestingRecord;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import com.bloodbank.bloodbank.service.TestingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/testing/results")
@RequiredArgsConstructor
public class TestingResultsController {

    private final TestingService testingService;

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TestOverallStatus status) {
        return ControllerUtils.paged(testingService.listTests(status, page, limit, sort));
    }

    @GetMapping("/{id}")
    public ApiResponse<TestingRecord> getById(@PathVariable UUID id) {
        return ApiResponse.ok(testingService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TestingRecord>> create(@Valid @RequestBody CreateTestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(testingService.createTest(request.getCollectionId())));
    }

    @PatchMapping("/{id}")
    public ApiResponse<TestingRecord> update(@PathVariable UUID id, @Valid @RequestBody UpdateTestResultsRequest request) {
        return ApiResponse.ok(testingService.updateResults(id, request.getTests()));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TestingRecord> complete(@PathVariable UUID id, @Valid @RequestBody CompleteTestRequest request) {
        return ApiResponse.ok(testingService.completeTest(id, request.getOverallStatus()));
    }
}
