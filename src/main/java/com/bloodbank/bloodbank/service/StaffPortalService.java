package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.Transaction;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodRequestRepository;
import com.bloodbank.bloodbank.repository.CollectionRepository;
import com.bloodbank.bloodbank.repository.CompensationPaymentRepository;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.DonorRewardRepository;
import com.bloodbank.bloodbank.repository.TransactionRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffPortalService {

    private final StaffService staffService;
    private final CollectionService collectionService;
    private final BloodRequestRepository bloodRequestRepository;
    private final CollectionRepository collectionRepository;
    private final CompensationPaymentRepository compensationPaymentRepository;
    private final DonorRepository donorRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final TransactionRepository transactionRepository;
    private final DisplayCodeService displayCodeService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile() {
        requireStaff();
        Staff staff = staffService.getByCurrentUser();
        return enrichStaffProfile(staff);
    }

    public Map<String, Object> updateProfile(Map<String, Object> updates) {
        requireStaff();
        String email = updates.get("email") != null ? String.valueOf(updates.get("email")) : null;
        String phone = updates.get("phone") != null ? String.valueOf(updates.get("phone")) : null;
        Staff staff = staffService.updateCurrentProfile(email, phone);
        activityLogService.log(ActionType.update, "update_staff_profile",
                "Staff updated profile", "staff", null, null, null, null, null);
        return enrichStaffProfile(staff);
    }

    private Map<String, Object> enrichStaffProfile(Staff staff) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", staff.getId());
        profile.put("name", staff.getName());
        profile.put("email", staff.getEmail());
        profile.put("phone", staff.getPhone() != null ? staff.getPhone() : staff.getUser().getPhone());
        profile.put("department", staff.getDepartment());
        profile.put("shift", staff.getShift());
        profile.put("status", staff.getStatus());
        profile.put("joinDate", staff.getJoinDate());
        profile.put("certifications", staff.getCertifications());
        if (staff.getStaffRole() != null) {
            profile.put("staffRole", staff.getStaffRole().getName());
            profile.put("role", staff.getStaffRole().getName());
        }
        return profile;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        requireStaff();
        Staff staff = staffService.getByCurrentUser();
        var activeSession = collectionService.getActiveSession();
        LocalDate today = LocalDate.now();
        long pendingRequests = bloodRequestRepository.findByStatus(RequestStatus.Pending,
                PageRequest.of(0, 1)).getTotalElements();
        var allCollections = collectionRepository.findAll();
        long todayCollections = allCollections.stream()
                .filter(c -> c.getCollectionDate() != null && c.getCollectionDate().equals(today))
                .count();
        long pendingTests = allCollections.stream()
                .filter(c -> (c.getStatus() == CollectionStatus.Collected
                        || c.getStatus() == CollectionStatus.Testing)
                        && (c.getTestResult() == null || c.getTestResult() == TestOverallStatus.Pending))
                .count();
        long completedToday = allCollections.stream()
                .filter(c -> c.getCollectionDate() != null && c.getCollectionDate().equals(today)
                        && (c.getStatus() == CollectionStatus.Stored || c.getStatus() == CollectionStatus.Tested))
                .count();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("staff", staff);
        dashboard.put("activeSession", activeSession);
        dashboard.put("pendingRequests", pendingRequests);
        dashboard.put("todayCollections", todayCollections);
        dashboard.put("pendingTests", pendingTests);
        dashboard.put("completedToday", completedToday);
        dashboard.put("permissions", staffService.getCurrentUserPermissions());
        return dashboard;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listPayments(PaymentStatus status, int page, int limit) {
        requireStaff();
        var pageable = PageUtils.toPageRequest(page, limit, "-createdAt");
        var result = status != null
                ? compensationPaymentRepository.findByStatus(status, pageable)
                : compensationPaymentRepository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichPayment)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    private Map<String, Object> enrichPayment(CompensationPayment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", payment.getId());
        row.put("donorId", payment.getDonorId());
        row.put("collectionId", payment.getCollectionId());
        row.put("amount", payment.getAmount());
        row.put("method", payment.getMethod());
        row.put("paymentMethod", payment.getMethod());
        row.put("status", payment.getStatus());
        row.put("paidAt", payment.getPaidAt());
        row.put("createdAt", payment.getCreatedAt());

        donorRepository.findById(payment.getDonorId()).ifPresent(donor -> {
            row.put("donorDisplayCode", donor.getDisplayCode());
            row.put("donorName", donor.getFirstName() + " " + donor.getLastName());
            if (donor.getBloodGroup() != null) {
                row.put("bloodGroup", donor.getBloodGroup().getValue());
            }
            if (donor.getUser() != null) {
                row.put("donorPhone", donor.getUser().getPhone());
            }
        });

        if (payment.getCollectionId() != null) {
            collectionRepository.findById(payment.getCollectionId()).ifPresent(collection -> {
                row.put("collectionDisplayCode", collection.getDisplayCode());
                row.put("collectionDate", collection.getCollectionDate());
                row.put("collectionTime", collection.getCollectionTime());
                row.put("bloodProductType", collection.getBloodProductType());
                row.put("volume", collection.getVolumeMl());
                row.put("location", collection.getLocation());
                if (!row.containsKey("bloodGroup") && collection.getBloodGroup() != null) {
                    row.put("bloodGroup", collection.getBloodGroup().getValue());
                }
            });
        }
        return row;
    }

    public CompensationPayment markPaymentPaid(UUID paymentId, CompensationMethod method) {
        return markPaymentPaid(paymentId, method, null);
    }

    public CompensationPayment markPaymentPaid(UUID paymentId, CompensationMethod method, String simulatorReference) {
        requireStaff();
        return completeCompensationPayment(paymentId, method, simulatorReference, true);
    }

    /** Marks a pending compensation payment as paid (used by staff payout and donor self-withdrawal). */
    public CompensationPayment completeCompensationPayment(UUID paymentId, CompensationMethod method,
                                                         String simulatorReference, boolean notifyDonor) {
        CompensationPayment payment = compensationPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Compensation payment"));
        if (payment.getStatus() == PaymentStatus.Paid) {
            return payment;
        }
        payment.setStatus(PaymentStatus.Paid);
        payment.setMethod(method);
        payment.setPaidAt(LocalDateTime.now());
        CompensationPayment saved = compensationPaymentRepository.save(payment);

        donorRewardRepository.findByDonorId(saved.getDonorId()).ifPresent(reward -> {
            reward.setPendingPayment(Math.max(0, reward.getPendingPayment() - saved.getAmount()));
            donorRewardRepository.save(reward);
        });

        transactionRepository.save(Transaction.builder()
                .displayCode(displayCodeService.nextCode(EntityType.TRANSACTION))
                .type(TransactionType.Expense)
                .category("Donor Compensation")
                .description(buildCompensationDescription(saved.getCollectionId(), simulatorReference))
                .amount(-saved.getAmount())
                .status(TransactionStatus.Completed)
                .referenceId(saved.getId().toString())
                .referenceType("compensation_payment")
                .paymentMethod(method != null ? method.name() : "Cash")
                .build());

        if (notifyDonor) {
            donorRepository.findById(saved.getDonorId()).ifPresent(donor ->
                    notificationService.notifyDonor(donor.getUser().getId(),
                            "Compensation paid", "Your compensation of " + saved.getAmount() + " has been paid.",
                            "compensation", saved.getId().toString()));
        }

        activityLogService.log(ActionType.update, "mark_payment_paid",
                "Marked compensation payment as paid", "finance", null, saved.getDonorId(), null, null, null);
        return saved;
    }

    private String buildCompensationDescription(UUID collectionId, String simulatorReference) {
        String base = "Compensation payment for collection " + collectionId;
        if (simulatorReference != null && !simulatorReference.isBlank()) {
            return base + " [SimRef: " + simulatorReference + "]";
        }
        return base;
    }

    private void requireStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Staff required", HttpStatus.FORBIDDEN);
        }
    }
}
