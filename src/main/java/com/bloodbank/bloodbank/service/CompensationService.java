package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.dto.request.PayCompensationRequest;
import com.bloodbank.bloodbank.entity.CompensationPayment;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.CompensationPaymentRepository;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CompensationService {

    private final CompensationPaymentRepository compensationPaymentRepository;
    private final StaffPortalService staffPortalService;
    private final PaymentSimulatorService paymentSimulatorService;

    @Transactional(readOnly = true)
    public Map<String, Object> listCompensations(PaymentStatus status, int page, int limit) {
        requireStaff();
        return staffPortalService.listPayments(status, page, limit);
    }

    @Transactional(readOnly = true)
    public CompensationPayment getById(UUID id) {
        requireStaff();
        return compensationPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compensation payment"));
    }

    public Map<String, Object> pay(UUID id, PayCompensationRequest request) {
        requireStaff();
        CompensationPayment payment = getById(id);
        if (payment.getStatus() == PaymentStatus.Paid) {
            return Map.of("payment", payment, "simulator", Map.of("success", true, "message", "Already paid"));
        }

        var method = PaymentSimulatorService.parseMethod(
                request != null ? request.getPaymentMethod() : null);
        String phone = request != null ? request.getPhoneNumber() : null;

        Map<String, Object> simulator = paymentSimulatorService.simulate(
                method,
                payment.getAmount() != null ? payment.getAmount() : 0,
                phone);

        String reference = String.valueOf(simulator.get("reference"));
        CompensationPayment saved = staffPortalService.markPaymentPaid(id, method, reference);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("payment", saved);
        response.put("simulator", simulator);
        return response;
    }

    private void requireStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Staff required", HttpStatus.FORBIDDEN);
        }
    }
}
