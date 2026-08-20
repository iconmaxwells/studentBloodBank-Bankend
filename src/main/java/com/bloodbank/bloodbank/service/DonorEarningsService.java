package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.dto.request.WithdrawEarningsRequest;
import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CompensationMethod;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.WithdrawalStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.repository.CompensationPaymentRepository;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.DonorRewardRepository;
import com.bloodbank.bloodbank.repository.EarningsWithdrawalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DonorEarningsService {

    private final DonorRepository donorRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final CompensationPaymentRepository compensationPaymentRepository;
    private final EarningsWithdrawalRepository earningsWithdrawalRepository;
    private final PaymentSimulatorService paymentSimulatorService;
    private final StaffPortalService staffPortalService;
    private final SystemSettingsService systemSettingsService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> getEarningsSummary(UUID donorId) {
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Donor not found", HttpStatus.NOT_FOUND));

        DonorReward reward = donorRewardRepository.findByDonorId(donorId)
                .orElse(DonorReward.builder().donorId(donorId).build());

        List<CompensationPayment> pendingPayments = compensationPaymentRepository
                .findByDonorIdAndStatusOrderByCreatedAtAsc(donorId, PaymentStatus.Pending);
        double pendingTotal = pendingPayments.stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0)
                .sum();

        List<CompensationPayment> paidPayments = compensationPaymentRepository
                .findByDonorIdAndStatusOrderByCreatedAtAsc(donorId, PaymentStatus.Paid);
        CompensationPayment lastPaid = paidPayments.isEmpty() ? null : paidPayments.get(paidPayments.size() - 1);

        SystemSettings settings = systemSettingsService.getSettings();
        boolean isVoluntary = Boolean.TRUE.equals(donor.getIsVoluntary());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("isVoluntary", isVoluntary);
        summary.put("compensationRate", isVoluntary ? 0 : settings.getDonorCompensationDefault());
        summary.put("totalEarnings", reward.getTotalEarnings() != null ? reward.getTotalEarnings() : 0);
        summary.put("pendingPayment", pendingTotal);
        summary.put("availableForWithdrawal", isVoluntary ? 0 : pendingTotal);
        summary.put("totalWithdrawn", reward.getTotalRedeemed() != null ? reward.getTotalRedeemed() : 0);
        summary.put("preferredPayoutMethod", donor.getPreferredPayoutMethod());
        summary.put("payoutPhoneNumber", donor.getPayoutPhoneNumber());
        summary.put("lastPaymentDate", lastPaid != null ? lastPaid.getPaidAt() : null);
        summary.put("lastPaymentAmount", lastPaid != null ? lastPaid.getAmount() : null);
        summary.put("pendingPaymentsCount", pendingPayments.size());
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRewardsView(UUID donorId) {
        DonorReward reward = donorRewardRepository.findByDonorId(donorId)
                .orElse(DonorReward.builder().donorId(donorId).build());
        Map<String, Object> earnings = getEarningsSummary(donorId);

        List<Map<String, Object>> paymentHistory = new ArrayList<>();
        compensationPaymentRepository.findByDonorId(donorId, PageRequest.of(0, 50))
                .getContent()
                .forEach(payment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", payment.getId());
                    row.put("amount", payment.getAmount());
                    row.put("status", payment.getStatus());
                    row.put("method", payment.getMethod());
                    row.put("paidAt", payment.getPaidAt());
                    row.put("date", payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt());
                    row.put("donations", 1);
                    paymentHistory.add(row);
                });

        List<Map<String, Object>> withdrawals = new ArrayList<>();
        earningsWithdrawalRepository.findByDonorIdOrderByCreatedAtDesc(donorId, PageRequest.of(0, 20))
                .getContent()
                .forEach(w -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", w.getId());
                    row.put("amount", w.getAmount());
                    row.put("method", w.getMethod());
                    row.put("status", w.getStatus());
                    row.put("referenceCode", w.getReferenceCode());
                    row.put("date", w.getCompletedAt() != null ? w.getCompletedAt() : w.getCreatedAt());
                    withdrawals.add(row);
                });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("level", reward.getLevel() != null ? reward.getLevel().name() : "Bronze");
        response.put("points", reward.getPoints() != null ? reward.getPoints() : 0);
        response.put("currentPoints", reward.getPoints() != null ? reward.getPoints() : 0);
        response.put("totalDonations", reward.getTotalDonations() != null ? reward.getTotalDonations() : 0);
        response.put("streak", reward.getStreak() != null ? reward.getStreak() : 0);
        response.put("totalEarnings", earnings.get("totalEarnings"));
        response.put("pendingPayment", earnings.get("pendingPayment"));
        response.put("availableForWithdrawal", earnings.get("availableForWithdrawal"));
        response.put("totalRedeemed", earnings.get("totalWithdrawn"));
        response.put("totalWithdrawn", earnings.get("totalWithdrawn"));
        response.put("isVoluntary", earnings.get("isVoluntary"));
        response.put("compensationRate", earnings.get("compensationRate"));
        response.put("preferredPayoutMethod", earnings.get("preferredPayoutMethod"));
        response.put("payoutPhoneNumber", earnings.get("payoutPhoneNumber"));
        response.put("paymentHistory", paymentHistory);
        response.put("withdrawals", withdrawals);
        return response;
    }

    public Map<String, Object> withdrawEarnings(UUID donorId, WithdrawEarningsRequest request) {
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new ApiException("NOT_FOUND", "Donor not found", HttpStatus.NOT_FOUND));

        if (Boolean.TRUE.equals(donor.getIsVoluntary())) {
            throw new BusinessRuleException("VOLUNTARY_DONOR",
                    "Voluntary donors do not receive monetary compensation");
        }

        List<CompensationPayment> pendingPayments = compensationPaymentRepository
                .findByDonorIdAndStatusOrderByCreatedAtAsc(donorId, PaymentStatus.Pending);
        if (pendingPayments.isEmpty()) {
            throw new BusinessRuleException("NO_PENDING_EARNINGS", "No earnings available to withdraw");
        }

        double totalAmount = pendingPayments.stream()
                .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0)
                .sum();
        if (totalAmount <= 0) {
            throw new BusinessRuleException("NO_PENDING_EARNINGS", "No earnings available to withdraw");
        }

        CompensationMethod method = donor.getPreferredPayoutMethod();
        if (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()) {
            method = PaymentSimulatorService.parseMethod(request.getPaymentMethod());
        }
        if (method == null) {
            method = CompensationMethod.Mobile_Money;
        }

        String destination = request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                ? request.getPhoneNumber().trim()
                : donor.getPayoutPhoneNumber();
        if (destination == null || destination.isBlank()) {
            if (donor.getUser() != null && donor.getUser().getPhone() != null) {
                destination = donor.getUser().getPhone();
            }
        }
        if (destination == null || destination.isBlank()) {
            throw new BusinessRuleException("PAYOUT_DESTINATION_REQUIRED",
                    "Please provide a mobile money number for withdrawal");
        }

        donor.setPayoutPhoneNumber(destination);
        donor.setPreferredPayoutMethod(method);
        donorRepository.save(donor);

        Map<String, Object> simulator = paymentSimulatorService.simulate(method, totalAmount, destination);
        String reference = String.valueOf(simulator.get("reference"));

        EarningsWithdrawal withdrawal = earningsWithdrawalRepository.save(EarningsWithdrawal.builder()
                .donorId(donorId)
                .amount(totalAmount)
                .method(method)
                .destination(destination)
                .status(WithdrawalStatus.Completed)
                .referenceCode(reference)
                .paymentsCount(pendingPayments.size())
                .notes(request.getNotes())
                .completedAt(LocalDateTime.now())
                .build());

        for (CompensationPayment payment : pendingPayments) {
            staffPortalService.completeCompensationPayment(payment.getId(), method, reference, false);
        }

        donorRewardRepository.findByDonorId(donorId).ifPresent(reward -> {
            reward.setTotalRedeemed((reward.getTotalRedeemed() != null ? reward.getTotalRedeemed() : 0) + totalAmount);
            donorRewardRepository.save(reward);
        });

        if (donor.getUser() != null) {
            notificationService.notifyUser(
                    donor.getUser().getId(),
                    com.bloodbank.bloodbank.entity.enums.DomainEnums.NotificationType.success,
                    "Withdrawal completed",
                    "GH₵ " + totalAmount + " has been sent to your account.",
                    "earnings",
                    withdrawal.getId().toString());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("withdrawal", withdrawal);
        response.put("simulator", simulator);
        response.put("amount", totalAmount);
        response.put("paymentsProcessed", pendingPayments.size());
        response.put("message", "Withdrawal of GH₵ " + totalAmount + " completed successfully");
        return response;
    }
}
