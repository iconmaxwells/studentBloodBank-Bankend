package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentStatus;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DonorPortalService {

    private final DonorRepository donorRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final CompensationPaymentRepository compensationPaymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final CollectionRepository collectionRepository;
    private final DonorService donorService;

    public Map<String, Object> getDashboard() {
        Donor donor = getCurrentDonor();
        Map<String, Object> eligibility = donorService.checkEligibility(donor.getId());

        List<Appointment> upcoming = appointmentRepository.findByDonorId(donor.getId(), PageRequest.of(0, 5))
                .getContent().stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(LocalDate.now())
                        && a.getStatus() != AppointmentStatus.Cancelled)
                .toList();

        DonorReward reward = donorRewardRepository.findByDonorId(donor.getId()).orElse(null);

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("donor", donor);
        response.put("eligibility", eligibility);
        response.put("upcomingAppointments", upcoming);
        response.put("totalDonations", donor.getTotalDonations() != null ? donor.getTotalDonations() : 0);
        response.put("rewardPoints", reward != null && reward.getPoints() != null ? reward.getPoints() : 0);
        response.put("rewardLevel", reward != null && reward.getLevel() != null ? reward.getLevel().name() : "Bronze");
        return response;
    }

    public Donor getProfile() {
        return getCurrentDonor();
    }

    @Transactional
    public Donor updateProfile(Donor updates) {
        Donor donor = getCurrentDonor();
        if (updates.getAddress() != null) donor.setAddress(updates.getAddress());
        if (updates.getCity() != null) donor.setCity(updates.getCity());
        if (updates.getRegion() != null) donor.setRegion(updates.getRegion());
        if (updates.getPostalCode() != null) donor.setPostalCode(updates.getPostalCode());
        if (updates.getEmergencyContact() != null) donor.setEmergencyContact(updates.getEmergencyContact());
        return donorRepository.save(donor);
    }

    public Map<String, Object> getDonationHistory(int page, int limit) {
        Donor donor = getCurrentDonor();
        return donorService.getDonationHistory(donor.getId(), page, limit);
    }

    public DonorReward getRewards() {
        Donor donor = getCurrentDonor();
        return donorRewardRepository.findByDonorId(donor.getId())
                .orElse(DonorReward.builder().donorId(donor.getId()).build());
    }

    public Map<String, Object> getCompensation(int page, int limit) {
        Donor donor = getCurrentDonor();
        var payments = compensationPaymentRepository.findByDonorId(donor.getId(),
                org.springframework.data.domain.PageRequest.of(page - 1, limit));
        return Map.of(
                "items", payments.getContent(),
                "pendingTotal", payments.getContent().stream()
                        .filter(p -> p.getStatus() == com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus.Pending)
                        .mapToDouble(CompensationPayment::getAmount)
                        .sum()
        );
    }

    private Donor getCurrentDonor() {
        return donorRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
    }
}
