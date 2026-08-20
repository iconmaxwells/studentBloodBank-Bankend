package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class DonorService {

    private final DonorRepository donorRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CollectionRepository collectionRepository;
    private final ScreeningRecordRepository screeningRecordRepository;
    private final BloodUnitRepository bloodUnitRepository;
    private final DonorRewardRepository donorRewardRepository;
    private final DisplayCodeService displayCodeService;
    private final SystemSettingsService systemSettingsService;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Map<String, Object> listDonors(String search, DonorStatus status, int page, int limit, String sort) {
        requireStaffOrAdmin();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        String pattern = (search == null || search.isBlank()) ? null : "%" + search.trim().toLowerCase() + "%";
        Page<Donor> result = donorRepository.search(pattern, status, pageable);
        return Map.of(
                "items", result.getContent(),
                "meta", PageUtils.toMeta(result, page, limit)
        );
    }

    @Transactional(readOnly = true)
    public Donor getById(UUID id) {
        Donor donor = donorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        authorizeDonorAccess(donor);
        return donor;
    }

    @Transactional(readOnly = true)
    public Donor getByDisplayCode(String displayCode) {
        Donor donor = donorRepository.findByDisplayCode(displayCode)
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        authorizeDonorAccess(donor);
        return donor;
    }

    public Donor createDonor(Donor donor, String email, String password) {
        return createDonor(donor, email, password, null);
    }

    public Donor createDonor(Donor donor, String email, String password, String phone) {
        requireManageDonors();
        SystemSettings settings = systemSettingsService.getSettings();
        int age = Period.between(donor.getDateOfBirth(), LocalDate.now()).getYears();
        if (age < settings.getMinAge() || age > settings.getMaxAge()) {
            throw new BusinessRuleException("AGE_INVALID",
                    "Donor age must be between " + settings.getMinAge() + " and " + settings.getMaxAge());
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("EMAIL_EXISTS", "Email already registered", HttpStatus.CONFLICT);
        }
        if (donor.getIdNumber() != null && donorRepository.existsByIdNumber(donor.getIdNumber())) {
            throw new ApiException("ID_EXISTS", "ID number already registered", HttpStatus.CONFLICT);
        }
        Role role = roleRepository.findByName("donor")
                .orElseThrow(() -> new ApiException("ROLE_NOT_FOUND", "Donor role not found", HttpStatus.INTERNAL_SERVER_ERROR));

        User user = userRepository.save(User.builder()
                .name(donor.getFirstName() + " " + donor.getLastName())
                .email(email)
                .phone(phone != null && !phone.isBlank() ? phone.trim() : null)
                .password(passwordEncoder.encode(password))
                .role(role)
                .active(true)
                .emailVerified(true)
                .build());

        donor.setDisplayCode(displayCodeService.nextCode(EntityType.DONOR));
        donor.setUser(user);
        if (donor.getStatus() == null) {
            donor.setStatus(DonorStatus.Pending_Screening);
        }
        Donor saved = donorRepository.save(donor);
        donorRewardRepository.save(DonorReward.builder().donorId(saved.getId()).build());
        activityLogService.log(ActionType.create, "create_donor", "Staff created donor: " + saved.getDisplayCode(),
                "donor", null, saved.getId(), null, null, null);
        return saved;
    }

    public Donor updateDonor(UUID id, Donor updates) {
        requireManageDonors();
        Donor donor = donorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        applyDonorUpdates(donor, updates);
        Donor saved = donorRepository.save(donor);
        activityLogService.log(ActionType.update, "update_donor", "Updated donor: " + saved.getDisplayCode(),
                "donor", null, saved.getId(), null, null, null);
        return saved;
    }

    public void deleteDonor(UUID id) {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
        Donor donor = donorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        donorRepository.delete(donor);
        userRepository.delete(donor.getUser());
        activityLogService.log(ActionType.delete, "delete_donor", "Soft deleted donor: " + donor.getDisplayCode(),
                "donor", null, donor.getId(), null, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkEligibility(UUID donorId) {
        Donor donor = getById(donorId);
        SystemSettings settings = systemSettingsService.getSettings();
        List<String> reasons = new ArrayList<>();
        boolean eligible = true;

        int age = Period.between(donor.getDateOfBirth(), LocalDate.now()).getYears();
        if (age < settings.getMinAge() || age > settings.getMaxAge()) {
            eligible = false;
            reasons.add("Age outside allowed range");
        }
        if (donor.getWeight() != null && donor.getWeight() < settings.getMinWeightKg()) {
            eligible = false;
            reasons.add("Weight below minimum " + settings.getMinWeightKg() + " kg");
        }
        if (donor.getStatus() == DonorStatus.Not_Eligible || donor.getStatus() == DonorStatus.Deferred) {
            eligible = false;
            reasons.add("Donor status: " + donor.getStatus());
        }
        if (donor.getNextEligibleDate() != null && LocalDate.now().isBefore(donor.getNextEligibleDate())) {
            eligible = false;
            reasons.add("Must wait until " + donor.getNextEligibleDate());
        }

        String role = SecurityUtils.getCurrentUserRole();
        boolean statusOk = donor.getStatus() == DonorStatus.Eligible
                || ("admin".equalsIgnoreCase(role) && donor.getStatus() == DonorStatus.Pending_Screening);

        return Map.of(
                "donorId", donor.getId(),
                "displayCode", donor.getDisplayCode(),
                "status", donor.getStatus(),
                "eligible", eligible && statusOk,
                "nextEligibleDate", donor.getNextEligibleDate() != null ? donor.getNextEligibleDate() : LocalDate.now(),
                "reasons", reasons
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDonationHistory(UUID donorId, int page, int limit) {
        Donor donor = getById(donorId);
        PageRequest pageable = PageUtils.toPageRequest(page, limit, "-collectionDate");
        Page<com.bloodbank.bloodbank.entity.Collection> collections = collectionRepository.findByDonorId(donor.getId(), pageable);
        return Map.of(
                "items", collections.getContent(),
                "meta", PageUtils.toMeta(collections, page, limit)
        );
    }

    public void updateDonationDates(Donor donor, BloodProductType productType) {
        SystemSettings settings = systemSettingsService.getSettings();
        LocalDate today = LocalDate.now();
        donor.setLastDonationDate(today);
        int interval = productType == BloodProductType.PLT ? 7 : settings.getMinDonationIntervalDays();
        donor.setNextEligibleDate(today.plusDays(interval));
        donor.setTotalDonations((donor.getTotalDonations() != null ? donor.getTotalDonations() : 0) + 1);
        donorRepository.save(donor);
    }

    /** Fill missing donor blood groups from screening, collections, or inventory history. */
    public int backfillMissingBloodGroups() {
        int updated = 0;
        for (Donor donor : donorRepository.findAll()) {
            if (donor.getBloodGroup() != null) continue;
            BloodGroup resolved = resolveBloodGroupForDonor(donor.getId());
            if (resolved != null) {
                donor.setBloodGroup(resolved);
                donorRepository.save(donor);
                updated++;
            }
        }
        return updated;
    }

    private BloodGroup resolveBloodGroupForDonor(UUID donorId) {
        return screeningRecordRepository
                .findFirstByDonorIdAndBloodGroupIsNotNullOrderByUpdatedAtDesc(donorId)
                .map(ScreeningRecord::getBloodGroup)
                .or(() -> collectionRepository
                        .findFirstByDonorIdAndBloodGroupIsNotNullOrderByCollectionDateDesc(donorId)
                        .map(com.bloodbank.bloodbank.entity.Collection::getBloodGroup))
                .or(() -> bloodUnitRepository
                        .findFirstByDonorIdOrderByCreatedAtDesc(donorId)
                        .map(BloodUnit::getBloodGroup))
                .orElse(null);
    }

    private void applyDonorUpdates(Donor donor, Donor updates) {
        if (updates.getFirstName() != null) donor.setFirstName(updates.getFirstName());
        if (updates.getLastName() != null) donor.setLastName(updates.getLastName());
        if (updates.getDateOfBirth() != null) donor.setDateOfBirth(updates.getDateOfBirth());
        if (updates.getGender() != null) donor.setGender(updates.getGender());
        if (updates.getBloodGroup() != null) donor.setBloodGroup(updates.getBloodGroup());
        if (updates.getIdType() != null) donor.setIdType(updates.getIdType());
        if (updates.getIdNumber() != null) donor.setIdNumber(updates.getIdNumber());
        if (updates.getAddress() != null) donor.setAddress(updates.getAddress());
        if (updates.getCity() != null) donor.setCity(updates.getCity());
        if (updates.getRegion() != null) donor.setRegion(updates.getRegion());
        if (updates.getPostalCode() != null) donor.setPostalCode(updates.getPostalCode());
        if (updates.getWeight() != null) donor.setWeight(updates.getWeight());
        if (updates.getHeight() != null) donor.setHeight(updates.getHeight());
        if (updates.getStatus() != null) donor.setStatus(updates.getStatus());
        if (updates.getNextEligibleDate() != null) donor.setNextEligibleDate(updates.getNextEligibleDate());
        if (updates.getIsVoluntary() != null) donor.setIsVoluntary(updates.getIsVoluntary());
        if (updates.getPreferredPayoutMethod() != null) donor.setPreferredPayoutMethod(updates.getPreferredPayoutMethod());
        if (updates.getPayoutPhoneNumber() != null) donor.setPayoutPhoneNumber(updates.getPayoutPhoneNumber());
        if (updates.getEmergencyContact() != null) donor.setEmergencyContact(updates.getEmergencyContact());
        if (updates.getMedicalHistory() != null) donor.setMedicalHistory(updates.getMedicalHistory());
    }

    private void authorizeDonorAccess(Donor donor) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("donor".equalsIgnoreCase(role)) {
            UUID userId = SecurityUtils.getCurrentUserId();
            if (!donor.getUser().getId().equals(userId)) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStaffOrAdmin() {
        if (!isStaffOrAdmin(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Staff or admin required", HttpStatus.FORBIDDEN);
        }
    }

    private void requireManageDonors() {
        if ("admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            return;
        }
        if (SecurityUtils.getCurrentUser() != null
                && SecurityUtils.getCurrentUser().hasPermission("canManageDonors")) {
            return;
        }
        throw new ApiException("FORBIDDEN", "Insufficient permissions", HttpStatus.FORBIDDEN);
    }

    private boolean isStaffOrAdmin(String role) {
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role)
                || "specialist".equalsIgnoreCase(role);
    }
}
