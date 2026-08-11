package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.ScreeningRecord;
import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EligibilityResult;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ScreeningStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.BusinessRuleException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.AppointmentRepository;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.ScreeningRecordRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ScreeningService {

    private final ScreeningRecordRepository screeningRecordRepository;
    private final DonorRepository donorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final TestingService testingService;
    private final ScreeningRecordEnricher screeningRecordEnricher;

    @Transactional(readOnly = true)
    public Map<String, Object> listScreenings(UUID donorId, int page, int limit, String sort) {
        requireScreeningAccess();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<ScreeningRecord> result = donorId != null
                ? screeningRecordRepository.findByDonorId(donorId, pageable)
                : screeningRecordRepository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(screeningRecordEnricher::enrich)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public ScreeningRecord getById(UUID id) {
        requireScreeningAccess();
        return screeningRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Screening record"));
    }

    @Transactional(readOnly = true)
    public ScreeningRecord getActiveSessionForDonor(UUID donorId) {
        requireScreeningAccess();
        return screeningRecordRepository
                .findFirstByDonorIdAndStatusOrderByUpdatedAtDesc(donorId, ScreeningStatus.In_Progress)
                .orElseThrow(() -> new ResourceNotFoundException("Active screening session"));
    }

    public ScreeningRecord createScreening(ScreeningRecord screening) {
        requireScreeningAccess();
        Donor donor = donorRepository.findById(screening.getDonorId())
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        if (screening.getStaffId() == null) {
            screening.setStaffId(SecurityUtils.getCurrentUserId());
        }
        if ("specialist".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            screening.setSpecialistId(SecurityUtils.getCurrentUserId());
        }
        screening.setStatus(ScreeningStatus.In_Progress);
        screening.setScreeningDate(LocalDate.now());
        screening.setScreeningTime(LocalTime.now());
        ScreeningRecord saved = screeningRecordRepository.save(screening);
        donor.setStatus(DonorStatus.Pending_Screening);
        donorRepository.save(donor);
        activityLogService.log(ActionType.create, "start_screening", "Started screening for donor",
                "screening", null, donor.getId(), null, null, null);
        return saved;
    }

    public ScreeningRecord updateScreening(UUID id, ScreeningRecord updates) {
        requireScreeningAccess();
        ScreeningRecord screening = getById(id);
        if (screening.getStatus() != ScreeningStatus.In_Progress) {
            throw new BusinessRuleException("SCREENING_CLOSED", "Screening is no longer in progress");
        }
        applyUpdates(screening, updates);
        assignSpecialistIfNeeded(screening);
        return screeningRecordRepository.save(screening);
    }

    public ScreeningRecord completeScreening(UUID id, EligibilityResult eligibilityResult,
                                           String deferralReason, LocalDate deferralUntil, String notes,
                                           BloodGroup bloodGroup, UUID appointmentId) {
        requireScreeningAccess();
        ScreeningRecord screening = getById(id);
        if (screening.getStatus() != ScreeningStatus.In_Progress) {
            throw new BusinessRuleException("SCREENING_CLOSED", "Screening is no longer in progress");
        }

        assignSpecialistIfNeeded(screening);

        screening.setEligibilityResult(eligibilityResult);
        screening.setDeferralReason(deferralReason);
        screening.setDeferralUntil(deferralUntil);
        if (notes != null) screening.setNotes(notes);
        if (bloodGroup != null) {
            screening.setBloodGroup(bloodGroup);
        }

        BloodGroup resolvedGroup = bloodGroup != null ? bloodGroup : screening.getBloodGroup();

        if (eligibilityResult == EligibilityResult.Eligible) {
            screening.setStatus(ScreeningStatus.Completed);
        } else if (eligibilityResult == EligibilityResult.Permanent_Deferral) {
            screening.setStatus(ScreeningStatus.Failed);
        } else {
            screening.setStatus(ScreeningStatus.Deferred);
        }

        setDonorEligibility(screening.getDonorId(), eligibilityResult, deferralUntil, resolvedGroup);
        ScreeningRecord saved = screeningRecordRepository.save(screening);
        completeLinkedAppointment(appointmentId, screening.getDonorId());
        testingService.syncLabTestsFromScreening(saved);
        activityLogService.log(ActionType.update, "complete_screening",
                "Completed screening with result: " + eligibilityResult,
                "screening", null, screening.getDonorId(), null, null, null);
        return saved;
    }

    private void completeLinkedAppointment(UUID appointmentId, UUID donorId) {
        if (appointmentId != null) {
            appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
                appointment.setStatus(AppointmentStatus.Completed);
                appointmentRepository.save(appointment);
            });
            return;
        }
        appointmentRepository.findAll().stream()
                .filter(a -> donorId.equals(a.getDonorId()))
                .filter(a -> a.getStatus() == AppointmentStatus.In_Screening
                        || a.getStatus() == AppointmentStatus.Checked_In
                        || a.getStatus() == AppointmentStatus.Confirmed
                        || a.getStatus() == AppointmentStatus.Scheduled)
                .findFirst()
                .ifPresent(appointment -> {
                    appointment.setStatus(AppointmentStatus.Completed);
                    appointmentRepository.save(appointment);
                });
    }

    public void setDonorEligibility(UUID donorId, EligibilityResult result, LocalDate deferralUntil, BloodGroup bloodGroup) {
        Donor donor = donorRepository.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        if (bloodGroup != null) {
            donor.setBloodGroup(bloodGroup);
        }
        switch (result) {
            case Eligible -> donor.setStatus(DonorStatus.Eligible);
            case Deferred -> {
                donor.setStatus(DonorStatus.Deferred);
                donor.setNextEligibleDate(deferralUntil);
            }
            case Permanent_Deferral -> donor.setStatus(DonorStatus.Not_Eligible);
        }
        donorRepository.save(donor);

        if (result == EligibilityResult.Eligible) {
            notificationService.notifyDonor(donor.getUser().getId(), "Eligible to donate",
                    "You are now eligible to donate blood.", "donor", donor.getId().toString());
        }
    }

    private void applyUpdates(ScreeningRecord screening, ScreeningRecord updates) {
        if (updates.getPersonalInfo() != null) screening.setPersonalInfo(updates.getPersonalInfo());
        if (updates.getContactInfo() != null) screening.setContactInfo(updates.getContactInfo());
        if (updates.getIdentification() != null) screening.setIdentification(updates.getIdentification());
        if (updates.getPhysicalInfo() != null) screening.setPhysicalInfo(updates.getPhysicalInfo());
        if (updates.getMedicalHistory() != null) screening.setMedicalHistory(updates.getMedicalHistory());
        if (updates.getLifestyle() != null) screening.setLifestyle(updates.getLifestyle());
        if (updates.getVitals() != null) screening.setVitals(updates.getVitals());
        if (updates.getBloodGroup() != null) screening.setBloodGroup(updates.getBloodGroup());
        if (updates.getNotes() != null) screening.setNotes(updates.getNotes());
    }

    private void assignSpecialistIfNeeded(ScreeningRecord screening) {
        if ("specialist".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            screening.setSpecialistId(SecurityUtils.getCurrentUserId());
        }
    }

    private void requireScreeningAccess() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)
                && !"specialist".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }
}
