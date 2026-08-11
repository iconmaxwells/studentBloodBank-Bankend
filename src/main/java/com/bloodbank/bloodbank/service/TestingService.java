package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Collection;
import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.ScreeningRecord;
import com.bloodbank.bloodbank.entity.TestingRecord;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CollectionStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ScreeningStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.CollectionRepository;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.ScreeningRecordRepository;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TestingService {

    private static final EnumSet<ScreeningStatus> SCREENING_WITH_LAB_RESULTS = EnumSet.of(
            ScreeningStatus.Completed,
            ScreeningStatus.Failed,
            ScreeningStatus.Deferred
    );

    private final TestingRecordRepository testingRecordRepository;
    private final CollectionRepository collectionRepository;
    private final DonorRepository donorRepository;
    private final ScreeningRecordRepository screeningRecordRepository;
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
        return createTestForCollection(collection, SecurityUtils.getCurrentUserId());
    }

    public TestingRecord createTestForCollection(Collection collection, UUID technicianId) {
        collection.setStatus(CollectionStatus.Testing);
        collectionRepository.save(collection);

        List<Map<String, Object>> tests = resolveTestsForDonor(collection.getDonorId(), collection.getBloodGroup());
        TestOverallStatus initialStatus = BloodBankUtils.isInfectiousPanelComplete(tests)
                ? TestOverallStatus.Completed
                : TestOverallStatus.Pending;

        TestingRecord record = TestingRecord.builder()
                .collectionId(collection.getId())
                .donorId(collection.getDonorId())
                .technicianId(technicianId)
                .testDate(LocalDate.now())
                .tests(tests)
                .overallStatus(initialStatus)
                .build();
        TestingRecord saved = testingRecordRepository.save(record);
        activityLogService.log(ActionType.testing, "create_test", "Created test for collection",
                "testing", null, collection.getDonorId(), null, collection.getId(), null);
        if (initialStatus == TestOverallStatus.Completed) {
            notifyLabResultsReady(collection);
        }
        return saved;
    }

    public void syncLabTestsFromScreening(ScreeningRecord screening) {
        if (screening.getVitals() == null || !BloodBankUtils.hasScreeningLabResults(screening.getVitals())) {
            return;
        }

        UUID donorId = screening.getDonorId();
        String bloodGroupLabel = resolveBloodGroupLabel(screening.getBloodGroup());
        List<Map<String, Object>> tests = BloodBankUtils.buildTestPanelFromScreening(
                screening.getVitals(), bloodGroupLabel);

        List<TestingRecord> openRecords = testingRecordRepository.findByDonorIdAndOverallStatusIn(
                donorId, EnumSet.of(TestOverallStatus.Pending, TestOverallStatus.Completed));
        for (TestingRecord record : openRecords) {
            record.setTests(tests);
            record.setOverallStatus(TestOverallStatus.Completed);
            testingRecordRepository.save(record);
            collectionRepository.findById(record.getCollectionId()).ifPresent(this::notifyLabResultsReady);
        }

        collectionRepository.findByDonorId(donorId, PageRequest.of(0, 20)).getContent().stream()
                .filter(collection -> collection.getStatus() == CollectionStatus.Collected
                        || collection.getStatus() == CollectionStatus.Testing)
                .filter(collection -> testingRecordRepository.findByCollectionId(collection.getId()).isEmpty())
                .forEach(collection -> createTestForCollection(collection, screening.getSpecialistId() != null
                        ? screening.getSpecialistId()
                        : screening.getStaffId()));
    }

    public void ensureLabTestForCollection(Collection collection) {
        if (testingRecordRepository.findByCollectionId(collection.getId()).isPresent()) {
            return;
        }
        screeningRecordRepository.findFirstByDonorIdOrderByUpdatedAtDesc(collection.getDonorId())
                .filter(screening -> SCREENING_WITH_LAB_RESULTS.contains(screening.getStatus()))
                .filter(screening -> screening.getVitals() != null
                        && BloodBankUtils.hasScreeningLabResults(screening.getVitals()))
                .ifPresent(screening -> createTestForCollection(collection, SecurityUtils.getCurrentUserId()));
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
            inventoryService.finalizeBloodUnitAfterTest(collection, true);
        } else {
            collection.setStatus(CollectionStatus.Failed);
            inventoryService.finalizeBloodUnitAfterTest(collection, false);
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

    private List<Map<String, Object>> resolveTestsForDonor(UUID donorId, BloodGroup collectionBloodGroup) {
        return screeningRecordRepository.findFirstByDonorIdOrderByUpdatedAtDesc(donorId)
                .filter(screening -> SCREENING_WITH_LAB_RESULTS.contains(screening.getStatus()))
                .filter(screening -> screening.getVitals() != null
                        && BloodBankUtils.hasScreeningLabResults(screening.getVitals()))
                .map(screening -> {
                    String bloodGroup = resolveBloodGroupLabel(
                            collectionBloodGroup != null ? collectionBloodGroup : screening.getBloodGroup());
                    return BloodBankUtils.buildTestPanelFromScreening(screening.getVitals(), bloodGroup);
                })
                .orElseGet(BloodBankUtils::defaultTestPanel);
    }

    private String resolveBloodGroupLabel(BloodGroup bloodGroup) {
        return bloodGroup != null ? bloodGroup.getValue() : "";
    }

    private void notifyLabResultsReady(Collection collection) {
        notificationService.notifyStaff("Lab results ready for review",
                "Specialist screening results are available for collection "
                        + collection.getDisplayCode() + ". Approve or reject the sample.",
                "testing", collection.getId().toString());
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
