package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Collection;
import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.TestingRecord;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CollectionStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.CollectionRepository;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.TestingRecordRepository;
import com.bloodbank.bloodbank.util.BloodBankUtils;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TestingService {

    private final TestingRecordRepository testingRecordRepository;
    private final CollectionRepository collectionRepository;
    private final DonorRepository donorRepository;
    private final InventoryService inventoryService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> listTests(TestOverallStatus status, int page, int limit, String sort) {
        requireTestingAccess();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<TestingRecord> result = status != null
                ? testingRecordRepository.findByOverallStatus(status, pageable)
                : testingRecordRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public TestingRecord getById(UUID id) {
        requireTestingAccess();
        return testingRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Testing record"));
    }

    public TestingRecord createTest(UUID collectionId) {
        requireTestingAccess();
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection"));
        if (testingRecordRepository.findByCollectionId(collectionId).isPresent()) {
            throw new BusinessRuleException("TEST_EXISTS", "Testing record already exists for this collection");
        }
        collection.setStatus(CollectionStatus.Testing);
        collectionRepository.save(collection);

        TestingRecord record = TestingRecord.builder()
                .collectionId(collectionId)
                .donorId(collection.getDonorId())
                .technicianId(SecurityUtils.getCurrentUserId())
                .testDate(LocalDate.now())
                .tests(BloodBankUtils.defaultTestPanel())
                .overallStatus(TestOverallStatus.Pending)
                .build();
        TestingRecord saved = testingRecordRepository.save(record);
        activityLogService.log(ActionType.testing, "create_test", "Created test for collection",
                "testing", null, collection.getDonorId(), null, collectionId, null);
        return saved;
    }

    public TestingRecord updateResults(UUID id, List<Map<String, Object>> tests) {
        requireTestingAccess();
        TestingRecord record = getById(id);
        if (record.getOverallStatus() == TestOverallStatus.Passed
                || record.getOverallStatus() == TestOverallStatus.Failed) {
            throw new BusinessRuleException("TEST_COMPLETED", "Test is already finalized");
        }
        record.setTests(tests);
        if (record.getOverallStatus() == TestOverallStatus.Pending) {
            record.setOverallStatus(TestOverallStatus.Completed);
        }
        return testingRecordRepository.save(record);
    }

    public TestingRecord completeTest(UUID id, TestOverallStatus overallStatus) {
        requireTestingAccess();
        if (overallStatus == TestOverallStatus.Pending || overallStatus == TestOverallStatus.Completed) {
            throw new BusinessRuleException("INVALID_STATUS", "Overall status must be Passed or Failed");
        }
        TestingRecord record = getById(id);
        if (record.getOverallStatus() == TestOverallStatus.Passed
                || record.getOverallStatus() == TestOverallStatus.Failed) {
            throw new BusinessRuleException("TEST_COMPLETED", "Test is already finalized");
        }

        Collection collection = collectionRepository.findById(record.getCollectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Collection"));
        record.setOverallStatus(overallStatus);
        collection.setTestResult(overallStatus);

        if (overallStatus == TestOverallStatus.Passed) {
            collection.setStatus(CollectionStatus.Stored);
            inventoryService.createBloodUnitFromCollection(collection);
        } else {
            collection.setStatus(CollectionStatus.Failed);
            deferDonorAfterFailedTest(collection.getDonorId());
            notificationService.notifyStaff("Failed test result",
                    "Collection " + collection.getDisplayCode() + " failed infectious disease screening.",
                    "collection", collection.getId().toString());
        }

        collectionRepository.save(collection);
        TestingRecord saved = testingRecordRepository.save(record);
        activityLogService.log(ActionType.testing, "complete_test",
                "Test completed with status: " + overallStatus,
                "testing", null, collection.getDonorId(), null, collection.getId(), null);
        return saved;
    }

    private void deferDonorAfterFailedTest(UUID donorId) {
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        donor.setStatus(DonorStatus.Deferred);
        donor.setNextEligibleDate(LocalDate.now().plusMonths(6));
        donorRepository.save(donor);
    }

    private void requireTestingAccess() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)
                && !"specialist".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
