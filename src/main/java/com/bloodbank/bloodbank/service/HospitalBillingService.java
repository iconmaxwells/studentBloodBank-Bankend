package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.dto.request.PayCompensationRequest;
import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.HospitalServiceCharge;
import com.bloodbank.bloodbank.entity.Transaction;
import com.bloodbank.bloodbank.entity.SystemSettings;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.NotificationType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TransactionStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TransactionType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.HospitalRepository;
import com.bloodbank.bloodbank.repository.HospitalServiceChargeRepository;
import com.bloodbank.bloodbank.repository.TransactionRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class HospitalBillingService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final HospitalServiceChargeRepository chargeRepository;
    private final HospitalRepository hospitalRepository;
    private final TransactionRepository transactionRepository;
    private final SystemSettingsService systemSettingsService;
    private final DisplayCodeService displayCodeService;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final PaymentSimulatorService paymentSimulatorService;

    public Map<String, Object> generateMonthlyCharges(YearMonth billingPeriod) {
        requireAdmin();
        SystemSettings settings = systemSettingsService.getSettings();
        double defaultAmount = safeAmount(settings.getHospitalServiceChargeDefault());
        String period = billingPeriod.format(PERIOD_FORMAT);

        List<Hospital> hospitals = hospitalRepository.findAll().stream()
                .filter(h -> h.getStatus() == null || h.getStatus() == HospitalStatus.Active)
                .toList();

        List<Map<String, Object>> created = new ArrayList<>();
        int skipped = 0;

        for (Hospital hospital : hospitals) {
            if (chargeRepository.existsByHospitalIdAndBillingPeriod(hospital.getId(), period)) {
                skipped++;
                continue;
            }
            HospitalServiceCharge charge = issueCharge(hospital, period, defaultAmount, billingPeriod);
            created.add(enrichCharge(charge));
        }

        activityLogService.log(ActionType.create, "generate_hospital_charges",
                "Generated " + created.size() + " hospital service charge(s) for " + period,
                "finance", null, null, null, null, null);

        return Map.of(
                "billingPeriod", period,
                "createdCount", created.size(),
                "skippedCount", skipped,
                "items", created
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listCharges(UUID hospitalId, PaymentStatus status, String billingPeriod,
                                           int page, int limit, String sort) {
        String role = SecurityUtils.getCurrentUserRole();
        String effectiveSort = (sort == null || sort.isBlank()) ? "-issuedAt" : sort;
        PageRequest pageable = PageUtils.toPageRequest(page, limit, effectiveSort);
        Page<HospitalServiceCharge> result;

        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = getCurrentHospital();
            result = chargeRepository.findByHospitalId(hospital.getId(), pageable);
        } else if ("admin".equalsIgnoreCase(role)) {
            if (hospitalId != null && status != null) {
                result = chargeRepository.findByHospitalIdAndStatus(hospitalId, status, pageable);
            } else if (hospitalId != null) {
                result = chargeRepository.findByHospitalId(hospitalId, pageable);
            } else if (status != null) {
                result = chargeRepository.findByStatus(status, pageable);
            } else if (billingPeriod != null && !billingPeriod.isBlank()) {
                result = chargeRepository.findByBillingPeriod(billingPeriod, pageable);
            } else {
                result = chargeRepository.findAll(pageable);
            }
        } else {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }

        List<Map<String, Object>> items = result.getContent().stream().map(this::enrichCharge).toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    public Map<String, Object> markChargePaid(UUID chargeId) {
        requireAdmin();
        HospitalServiceCharge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital service charge"));

        if (charge.getStatus() == PaymentStatus.Paid) {
            throw new ApiException("INVALID_STATUS", "Charge is already paid", HttpStatus.BAD_REQUEST);
        }

        HospitalServiceCharge saved = finalizeChargePayment(charge, null, null, "mark_service_charge_paid",
                "Marked service charge " + charge.getDisplayCode() + " as paid");
        return enrichCharge(saved);
    }

    public Map<String, Object> simulatePayCharge(UUID chargeId, PayCompensationRequest request) {
        HospitalServiceCharge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital service charge"));
        requireChargeAccess(charge);

        if (charge.getStatus() == PaymentStatus.Paid) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("charge", enrichCharge(charge));
            response.put("simulator", Map.of("success", true, "message", "Invoice is already paid"));
            return response;
        }

        var method = PaymentSimulatorService.parseMethod(request != null ? request.getPaymentMethod() : null);
        String phone = request != null ? request.getPhoneNumber() : null;
        double amount = charge.getAmount() != null ? charge.getAmount() : 0;
        Map<String, Object> simulator = paymentSimulatorService.simulate(method, amount, phone);
        String reference = String.valueOf(simulator.get("reference"));

        HospitalServiceCharge saved = finalizeChargePayment(charge, method.name(), reference,
                "pay_service_charge",
                "Hospital paid service charge " + charge.getDisplayCode() + " via simulator");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("charge", enrichCharge(saved));
        response.put("simulator", simulator);
        return response;
    }

    private HospitalServiceCharge finalizeChargePayment(HospitalServiceCharge charge, String paymentMethod,
                                                        String simulatorReference, String action, String logMessage) {
        charge.setStatus(PaymentStatus.Paid);
        charge.setPaidAt(LocalDateTime.now());
        HospitalServiceCharge saved = chargeRepository.save(charge);

        if (saved.getTransactionId() != null) {
            transactionRepository.findById(saved.getTransactionId()).ifPresent(txn -> {
                txn.setStatus(TransactionStatus.Completed);
                if (paymentMethod != null && !paymentMethod.isBlank()) {
                    txn.setPaymentMethod(paymentMethod.replace('_', ' '));
                }
                if (simulatorReference != null && !simulatorReference.isBlank()) {
                    String base = txn.getDescription() != null ? txn.getDescription() : "";
                    txn.setDescription(base + " [SimRef: " + simulatorReference + "]");
                }
                transactionRepository.save(txn);
            });
        }

        hospitalRepository.findById(saved.getHospitalId()).ifPresent(hospital ->
                notificationService.notifyUser(
                        hospital.getUser().getId(),
                        NotificationType.info,
                        "Service charge paid",
                        "Your service charge " + saved.getDisplayCode() + " for " + saved.getBillingPeriod()
                                + " has been paid successfully.",
                        "service_charge",
                        saved.getId().toString()
                )
        );

        activityLogService.log(ActionType.update, action, logMessage,
                "finance", saved.getTransactionId(), null, saved.getHospitalId(), null, null);

        return saved;
    }

    private void requireChargeAccess(HospitalServiceCharge charge) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = getCurrentHospital();
            if (!hospital.getId().equals(charge.getHospitalId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!"admin".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    public void ensureMonthlyChargesForPeriod(YearMonth billingPeriod) {
        SystemSettings settings = systemSettingsService.getSettings();
        double defaultAmount = safeAmount(settings.getHospitalServiceChargeDefault());
        String period = billingPeriod.format(PERIOD_FORMAT);

        hospitalRepository.findAll().stream()
                .filter(h -> h.getStatus() == null || h.getStatus() == HospitalStatus.Active)
                .filter(h -> !chargeRepository.existsByHospitalIdAndBillingPeriod(h.getId(), period))
                .forEach(hospital -> issueCharge(hospital, period, defaultAmount, billingPeriod));
    }

    private HospitalServiceCharge issueCharge(Hospital hospital, String period, double amount, YearMonth billingPeriod) {
        String description = "Monthly blood bank service charge for " + formatPeriodLabel(billingPeriod);

        Transaction transaction = Transaction.builder()
                .displayCode(displayCodeService.nextCode(EntityType.TRANSACTION))
                .date(LocalDate.now())
                .type(TransactionType.Revenue)
                .category("Hospital Service Charge")
                .description(description + " — " + hospital.getName())
                .amount(amount)
                .status(TransactionStatus.Pending)
                .referenceId(hospital.getId().toString())
                .referenceType("hospital_service_charge")
                .paymentMethod("Invoice")
                .build();
        Transaction savedTxn = transactionRepository.save(transaction);

        HospitalServiceCharge charge = HospitalServiceCharge.builder()
                .displayCode(displayCodeService.nextCode(EntityType.SERVICE_CHARGE))
                .hospitalId(hospital.getId())
                .billingPeriod(period)
                .amount(amount)
                .status(PaymentStatus.Pending)
                .transactionId(savedTxn.getId())
                .description(description)
                .dueDate(billingPeriod.atEndOfMonth().plusDays(15))
                .build();
        HospitalServiceCharge saved = chargeRepository.save(charge);

        notificationService.notifyUser(
                hospital.getUser().getId(),
                NotificationType.info,
                "Monthly service charge issued",
                "Service charge " + saved.getDisplayCode() + " of GH₵ " + String.format("%.2f", amount)
                        + " for " + formatPeriodLabel(billingPeriod) + " is due by "
                        + saved.getDueDate() + ".",
                "service_charge",
                saved.getId().toString()
        );

        log.info("Issued service charge {} to hospital {} for {}", saved.getDisplayCode(), hospital.getName(), period);
        return saved;
    }

    private Map<String, Object> enrichCharge(HospitalServiceCharge charge) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", charge.getId());
        row.put("displayCode", charge.getDisplayCode());
        row.put("hospitalId", charge.getHospitalId());
        row.put("billingPeriod", charge.getBillingPeriod());
        row.put("billingPeriodLabel", formatPeriodLabel(YearMonth.parse(charge.getBillingPeriod(), PERIOD_FORMAT)));
        row.put("amount", charge.getAmount());
        row.put("status", charge.getStatus());
        row.put("transactionId", charge.getTransactionId());
        row.put("description", charge.getDescription());
        row.put("dueDate", charge.getDueDate());
        row.put("issuedAt", charge.getIssuedAt());
        row.put("paidAt", charge.getPaidAt());
        hospitalRepository.findById(charge.getHospitalId()).ifPresent(hospital -> {
            row.put("hospitalName", hospital.getName());
            row.put("hospitalCode", hospital.getDisplayCode());
        });
        return row;
    }

    private Hospital getCurrentHospital() {
        return hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }

    private static double safeAmount(Double value) {
        return value != null && value > 0 ? value : 500.0;
    }

    private static String formatPeriodLabel(YearMonth period) {
        return period.getMonth().name().substring(0, 1)
                + period.getMonth().name().substring(1).toLowerCase()
                + " " + period.getYear();
    }
}
