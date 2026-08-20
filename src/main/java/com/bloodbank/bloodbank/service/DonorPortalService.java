package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Gender;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DonorPortalService {

    private final DonorRepository donorRepository;
    private final UserRepository userRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final CompensationPaymentRepository compensationPaymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final DonorService donorService;
    private final DonorEarningsService donorEarningsService;

    public Map<String, Object> getDashboard() {
        Donor donor = getCurrentDonor();
        Map<String, Object> eligibility = donorService.checkEligibility(donor.getId());

        List<Appointment> upcoming = appointmentRepository.findByDonorId(donor.getId(), PageRequest.of(0, 5))
                .getContent().stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(LocalDate.now())
                        && a.getStatus() != AppointmentStatus.Cancelled)
                .toList();

        DonorReward reward = donorRewardRepository.findByDonorId(donor.getId()).orElse(null);
        Map<String, Object> earnings = donorEarningsService.getEarningsSummary(donor.getId());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("donor", enrichDonorProfile(donor));
        response.put("eligibility", eligibility);
        response.put("upcomingAppointments", upcoming);
        response.put("totalDonations", donor.getTotalDonations() != null ? donor.getTotalDonations() : 0);
        response.put("rewardPoints", reward != null && reward.getPoints() != null ? reward.getPoints() : 0);
        response.put("rewardLevel", reward != null && reward.getLevel() != null ? reward.getLevel().name() : "Bronze");
        response.put("totalEarnings", earnings.get("totalEarnings"));
        response.put("pendingPayment", earnings.get("pendingPayment"));
        response.put("availableForWithdrawal", earnings.get("availableForWithdrawal"));
        response.put("totalWithdrawn", earnings.get("totalWithdrawn"));
        response.put("isVoluntary", earnings.get("isVoluntary"));
        response.put("compensationRate", earnings.get("compensationRate"));
        response.put("lastPaymentDate", earnings.get("lastPaymentDate"));
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile() {
        return enrichDonorProfile(getCurrentDonor());
    }

    @Transactional
    public Map<String, Object> updateProfile(Map<String, Object> updates) {
        Donor donor = getCurrentDonor();
        User user = donor.getUser();
        if (user == null) {
            throw new ResourceNotFoundException("User");
        }

        if (updates.get("firstName") != null) {
            donor.setFirstName(String.valueOf(updates.get("firstName")).trim());
        }
        if (updates.get("lastName") != null) {
            donor.setLastName(String.valueOf(updates.get("lastName")).trim());
        }
        if (updates.get("address") != null) {
            donor.setAddress(blankToNull(String.valueOf(updates.get("address"))));
        }
        if (updates.get("city") != null) {
            donor.setCity(blankToNull(String.valueOf(updates.get("city"))));
        }
        if (updates.get("region") != null) {
            donor.setRegion(blankToNull(String.valueOf(updates.get("region"))));
        }
        if (updates.get("state") != null) {
            donor.setRegion(blankToNull(String.valueOf(updates.get("state"))));
        }
        if (updates.get("postalCode") != null) {
            donor.setPostalCode(blankToNull(String.valueOf(updates.get("postalCode"))));
        }
        if (updates.get("zipCode") != null) {
            donor.setPostalCode(blankToNull(String.valueOf(updates.get("zipCode"))));
        }
        if (updates.containsKey("weight")) {
            donor.setWeight(parseOptionalDouble(updates.get("weight")));
        }
        if (updates.containsKey("height")) {
            donor.setHeight(parseOptionalDouble(updates.get("height")));
        }
        if (updates.get("dateOfBirth") != null) {
            LocalDate dob = parseOptionalDate(String.valueOf(updates.get("dateOfBirth")));
            if (dob != null) {
                donor.setDateOfBirth(dob);
            }
        }
        if (updates.get("gender") != null) {
            Gender gender = parseOptionalGender(String.valueOf(updates.get("gender")));
            if (gender != null) {
                donor.setGender(gender);
            }
        }
        if (updates.get("phone") != null) {
            user.setPhone(blankToNull(String.valueOf(updates.get("phone"))));
        }
        if (updates.get("email") != null) {
            String email = String.valueOf(updates.get("email")).trim();
            if (!email.isEmpty() && !email.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmail(email)) {
                throw new ApiException("EMAIL_EXISTS", "Email already in use", HttpStatus.CONFLICT);
            }
            if (!email.isEmpty()) {
                user.setEmail(email);
            }
        }
        if (updates.get("emergencyContact") instanceof Map<?, ?> emergency) {
            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) emergency;
            donor.setEmergencyContact(contact);
        }
        if (updates.containsKey("isVoluntary")) {
            donor.setIsVoluntary(parseOptionalBoolean(updates.get("isVoluntary")));
        }
        if (updates.get("preferredPayoutMethod") != null) {
            donor.setPreferredPayoutMethod(
                    com.bloodbank.bloodbank.service.PaymentSimulatorService.parseMethod(
                            String.valueOf(updates.get("preferredPayoutMethod"))));
        }
        if (updates.get("payoutPhoneNumber") != null) {
            donor.setPayoutPhoneNumber(blankToNull(String.valueOf(updates.get("payoutPhoneNumber"))));
        }

        String firstName = donor.getFirstName();
        String lastName = donor.getLastName();
        if (firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank()) {
            user.setName(firstName + " " + lastName);
        }

        userRepository.save(user);
        donorRepository.save(donor);
        return enrichDonorProfile(donor);
    }

    public Map<String, Object> getDonationHistory(int page, int limit) {
        Donor donor = getCurrentDonor();
        return donorService.getDonationHistory(donor.getId(), page, limit);
    }

    public Map<String, Object> getRewards() {
        Donor donor = getCurrentDonor();
        return donorEarningsService.getRewardsView(donor.getId());
    }

    public Map<String, Object> getEarnings() {
        return donorEarningsService.getEarningsSummary(getCurrentDonor().getId());
    }

    @Transactional
    public Map<String, Object> withdrawEarnings(com.bloodbank.bloodbank.dto.request.WithdrawEarningsRequest request) {
        return donorEarningsService.withdrawEarnings(getCurrentDonor().getId(), request);
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

    private Map<String, Object> enrichDonorProfile(Donor donor) {
        User user = donor.getUser();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", donor.getId());
        profile.put("displayCode", donor.getDisplayCode());
        profile.put("firstName", donor.getFirstName());
        profile.put("lastName", donor.getLastName());
        profile.put("email", user != null ? user.getEmail() : null);
        profile.put("phone", user != null ? user.getPhone() : null);
        profile.put("dateOfBirth", donor.getDateOfBirth());
        profile.put("gender", donor.getGender());
        profile.put("bloodGroup", donor.getBloodGroup() != null ? donor.getBloodGroup().getValue() : null);
        profile.put("address", donor.getAddress());
        profile.put("city", donor.getCity());
        profile.put("region", donor.getRegion());
        profile.put("postalCode", donor.getPostalCode());
        profile.put("weight", donor.getWeight());
        profile.put("height", donor.getHeight());
        profile.put("status", donor.getStatus());
        profile.put("totalDonations", donor.getTotalDonations());
        profile.put("lastDonationDate", donor.getLastDonationDate());
        profile.put("nextEligibleDate", donor.getNextEligibleDate());
        profile.put("emergencyContact", donor.getEmergencyContact());
        profile.put("isVoluntary", Boolean.TRUE.equals(donor.getIsVoluntary()));
        profile.put("preferredPayoutMethod", donor.getPreferredPayoutMethod());
        profile.put("payoutPhoneNumber", donor.getPayoutPhoneNumber());
        profile.put("memberSince", donor.getCreatedAt());
        profile.put("registeredDate", donor.getCreatedAt());
        return profile;
    }

    private Donor getCurrentDonor() {
        return donorRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Double parseOptionalDouble(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ex) {
            throw new ApiException("INVALID_VALUE", "Invalid numeric value: " + text, HttpStatus.BAD_REQUEST);
        }
    }

    private static LocalDate parseOptionalDate(String value) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
        } catch (Exception ex) {
            throw new ApiException("INVALID_VALUE", "Invalid date of birth", HttpStatus.BAD_REQUEST);
        }
    }

    private static Gender parseOptionalGender(String value) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) {
            return null;
        }
        for (Gender gender : Gender.values()) {
            if (gender.name().equalsIgnoreCase(text)) {
                return gender;
            }
        }
        throw new ApiException("INVALID_VALUE", "Invalid gender: " + text, HttpStatus.BAD_REQUEST);
    }

    private static Boolean parseOptionalBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
