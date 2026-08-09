package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.*;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionSessionRepository sessionRepository;
    private final DonorRepository donorRepository;
    private final CompensationPaymentRepository compensationPaymentRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final DisplayCodeService displayCodeService;
    private final SystemSettingsService systemSettingsService;
    private final ActivityLogService activityLogService;
    private final DonorService donorService;

    @Transactional(readOnly = true)
    public Map<String, Object> listCollections(CollectionStatus status, int page, int limit, String sort) {
        requireStaff();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<Collection> result = status != null
                ? collectionRepository.findByStatus(status, pageable)
                : collectionRepository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichCollectionRow)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    private Map<String, Object> enrichCollectionRow(Collection collection) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", collection.getId());
        row.put("displayCode", collection.getDisplayCode());
        row.put("donorId", collection.getDonorId());
        row.put("staffId", collection.getStaffId());
        row.put("sessionId", collection.getSessionId());
        row.put("bloodGroup", collection.getBloodGroup());
        row.put("bloodProductType", collection.getBloodProductType());
        row.put("volumeMl", collection.getVolumeMl());
        row.put("bagNumber", collection.getBagNumber());
        row.put("collectionDate", collection.getCollectionDate());
        row.put("collectionTime", collection.getCollectionTime());
        row.put("location", collection.getLocation());
        row.put("bloodBankId", collection.getBloodBankId());
        row.put("preScreeningVitals", collection.getPreScreeningVitals());
        row.put("anticoagulant", collection.getAnticoagulant());
        row.put("storageLocation", collection.getStorageLocation());
        row.put("status", collection.getStatus());
        row.put("testResult", collection.getTestResult());
        row.put("compensationAmount", collection.getCompensationAmount());
        row.put("notes", collection.getNotes());
        row.put("createdAt", collection.getCreatedAt());
        row.put("updatedAt", collection.getUpdatedAt());
        donorRepository.findById(collection.getDonorId()).ifPresent(donor ->
                row.put("donorName", donor.getFirstName() + " " + donor.getLastName()));
        return row;
    }

    @Transactional(readOnly = true)
    public Collection getById(UUID id) {
        requireStaff();
        return collectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection"));
    }

    public Collection createCollection(Collection collection) {
        requireWithdrawalPermission();
        Donor donor = donorRepository.findById(collection.getDonorId())
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));

        Map<String, Object> eligibility = donorService.checkEligibility(donor.getId());
        if (!Boolean.TRUE.equals(eligibility.get("eligible"))) {
            throw new BusinessRuleException("DONOR_NOT_ELIGIBLE", "Donor is not eligible to donate");
        }

        collection.setDisplayCode(displayCodeService.nextCode(EntityType.COLLECTION));
        collection.setStaffId(SecurityUtils.getCurrentUserId());
        if (collection.getCollectionDate() == null) collection.setCollectionDate(LocalDate.now());
        if (collection.getCollectionTime() == null) collection.setCollectionTime(LocalTime.now());
        if (collection.getBloodGroup() == null) {
            collection.setBloodGroup(donor.getBloodGroup());
        }
        collection.setStatus(CollectionStatus.Collected);
        collection.setTestResult(TestOverallStatus.Pending);

        SystemSettings settings = systemSettingsService.getSettings();
        if (collection.getCompensationAmount() == null) {
            collection.setCompensationAmount(settings.getDonorCompensationDefault());
        }

        Collection saved = collectionRepository.save(collection);

        if (donor.getBloodGroup() == null && saved.getBloodGroup() != null) {
            donor.setBloodGroup(saved.getBloodGroup());
            donorRepository.save(donor);
        }

        if (saved.getCompensationAmount() != null && saved.getCompensationAmount() > 0) {
            compensationPaymentRepository.save(CompensationPayment.builder()
                    .donorId(donor.getId())
                    .collectionId(saved.getId())
                    .amount(saved.getCompensationAmount())
                    .status(PaymentStatus.Pending)
                    .build());
            updateDonorRewardCompensation(donor.getId(), saved.getCompensationAmount());
        }

        awardDonationPoints(donor);
        donorService.updateDonationDates(donor, saved.getBloodProductType());

        if (saved.getSessionId() != null) {
            sessionRepository.findById(saved.getSessionId()).ifPresent(session -> {
                session.setActualDonors(session.getActualDonors() + 1);
                sessionRepository.save(session);
            });
        }

        activityLogService.log(ActionType.collection, "record_collection",
                "Recorded collection " + saved.getDisplayCode(),
                "collection", null, donor.getId(), null, saved.getId(), null);
        return saved;
    }

    public Collection updateCollection(UUID id, Collection updates) {
        requireStaff();
        Collection collection = getById(id);
        if (updates.getVolumeMl() != null) collection.setVolumeMl(updates.getVolumeMl());
        if (updates.getBagNumber() != null) collection.setBagNumber(updates.getBagNumber());
        if (updates.getPreScreeningVitals() != null) collection.setPreScreeningVitals(updates.getPreScreeningVitals());
        if (updates.getAnticoagulant() != null) collection.setAnticoagulant(updates.getAnticoagulant());
        if (updates.getStorageLocation() != null) collection.setStorageLocation(updates.getStorageLocation());
        if (updates.getNotes() != null) collection.setNotes(updates.getNotes());
        if (updates.getStatus() != null) collection.setStatus(updates.getStatus());
        return collectionRepository.save(collection);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSessions(UUID staffId, int page, int limit) {
        requireStaff();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, "-startedAt");
        Page<CollectionSession> result = staffId != null
                ? sessionRepository.findByStaffId(staffId, pageable)
                : sessionRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    public CollectionSession startSession(CollectionSession session) {
        requireStaff();
        session.setStaffId(SecurityUtils.getCurrentUserId());
        session.setStatus(CollectionSessionStatus.Active);
        session.setStartedAt(LocalDateTime.now());
        if (session.getActualDonors() == null) session.setActualDonors(0);
        return sessionRepository.save(session);
    }

    public CollectionSession updateSession(UUID id, CollectionSession updates) {
        requireStaff();
        CollectionSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection session"));
        if (updates.getLocation() != null) session.setLocation(updates.getLocation());
        if (updates.getExpectedDonors() != null) session.setExpectedDonors(updates.getExpectedDonors());
        if (updates.getNotes() != null) session.setNotes(updates.getNotes());
        if (updates.getStatus() != null) {
            session.setStatus(updates.getStatus());
            if (updates.getStatus() == CollectionSessionStatus.Completed
                    || updates.getStatus() == CollectionSessionStatus.Cancelled) {
                session.setEndedAt(LocalDateTime.now());
            }
        }
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public CollectionSession getActiveSession() {
        requireStaff();
        return sessionRepository.findByStaffIdAndStatus(SecurityUtils.getCurrentUserId(), CollectionSessionStatus.Active)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        requireStaff();
        var collections = collectionRepository.findAll();
        long today = collections.stream()
                .filter(c -> c.getCollectionDate() != null
                        && c.getCollectionDate().equals(LocalDate.now()))
                .count();
        long thisMonth = collections.stream()
                .filter(c -> c.getCollectionDate() != null
                        && c.getCollectionDate().getYear() == LocalDate.now().getYear()
                        && c.getCollectionDate().getMonth() == LocalDate.now().getMonth())
                .count();
        Map<CollectionStatus, Long> byStatus = collections.stream()
                .filter(c -> c.getStatus() != null)
                .collect(java.util.stream.Collectors.groupingBy(Collection::getStatus, java.util.stream.Collectors.counting()));

        return Map.of(
                "totalCollections", collections.size(),
                "todayCollections", today,
                "monthCollections", thisMonth,
                "byStatus", byStatus,
                "activeSessions", sessionRepository.findAll().stream()
                        .filter(s -> s.getStatus() == CollectionSessionStatus.Active)
                        .count()
        );
    }

    private void awardDonationPoints(Donor donor) {
        DonorReward reward = donorRewardRepository.findByDonorId(donor.getId())
                .orElseGet(() -> donorRewardRepository.save(DonorReward.builder().donorId(donor.getId()).build()));
        int points = 100;
        int totalDonations = reward.getTotalDonations() != null ? reward.getTotalDonations() : 0;
        if (totalDonations == 0) {
            points += 50;
        }
        reward.setPoints((reward.getPoints() != null ? reward.getPoints() : 0) + points);
        reward.setTotalDonations(totalDonations + 1);
        reward.setLevel(BloodBankUtils.calculateRewardLevel(reward.getPoints()));
        donorRewardRepository.save(reward);
    }

    private void updateDonorRewardCompensation(UUID donorId, double amount) {
        donorRewardRepository.findByDonorId(donorId).ifPresent(reward -> {
            double pending = reward.getPendingPayment() != null ? reward.getPendingPayment() : 0.0;
            double earnings = reward.getTotalEarnings() != null ? reward.getTotalEarnings() : 0.0;
            reward.setPendingPayment(pending + amount);
            reward.setTotalEarnings(earnings + amount);
            donorRewardRepository.save(reward);
        });
    }

    private void requireStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Staff required", HttpStatus.FORBIDDEN);
        }
    }

    private void requireWithdrawalPermission() {
        if ("admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) return;
        if (SecurityUtils.getCurrentUser() != null
                && SecurityUtils.getCurrentUser().hasPermission("canConductWithdrawals")) {
            return;
        }
        throw new ApiException("FORBIDDEN", "Insufficient permissions", HttpStatus.FORBIDDEN);
    }
}
