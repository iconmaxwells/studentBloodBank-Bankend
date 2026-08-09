package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Appointment;
import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.ScreeningRecord;
import com.bloodbank.bloodbank.entity.Staff;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ScreeningStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.repository.AppointmentRepository;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.ScreeningRecordRepository;
import com.bloodbank.bloodbank.repository.StaffRepository;
import com.bloodbank.bloodbank.repository.TestingRecordRepository;
import com.bloodbank.bloodbank.util.PageUtils;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialistPortalService {

    private final ScreeningRecordRepository screeningRecordRepository;
    private final TestingRecordRepository testingRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final DonorRepository donorRepository;
    private final StaffRepository staffRepository;
    private final ScreeningService screeningService;
    private final TestingService testingService;

    public Map<String, Object> getDashboard() {
        requireSpecialist();
        UUID specialistId = SecurityUtils.getCurrentUserId();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);

        List<ScreeningRecord> allScreenings = screeningRecordRepository.findAll();
        long pendingScreenings = allScreenings.stream()
                .filter(s -> s.getStatus() == ScreeningStatus.In_Progress)
                .count();
        long completedToday = allScreenings.stream()
                .filter(s -> s.getStatus() == ScreeningStatus.Completed
                        && s.getScreeningDate() != null
                        && s.getScreeningDate().equals(today))
                .count();
        long weekScreenings = allScreenings.stream()
                .filter(s -> s.getScreeningDate() != null
                        && !s.getScreeningDate().isBefore(weekStart)
                        && !s.getScreeningDate().isAfter(today))
                .count();
        long todayAppointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getDate() != null && a.getDate().equals(today))
                .count();
        long donorsPendingScreening = donorRepository.search(null, DonorStatus.Pending_Screening,
                PageRequest.of(0, 1)).getTotalElements();
        long pendingTests = testingRecordRepository.findByOverallStatus(TestOverallStatus.Pending,
                PageRequest.of(0, 1)).getTotalElements();

        return Map.of(
                "pendingScreenings", pendingScreenings,
                "pendingTests", pendingTests,
                "todayAppointments", todayAppointments,
                "completedToday", completedToday,
                "weekScreenings", weekScreenings,
                "donorsPendingScreening", donorsPendingScreening,
                "specialistId", specialistId
        );
    }

    public Map<String, Object> getSchedules(int page, int limit) {
        requireSpecialist();
        var pageable = PageUtils.toPageRequest(page, limit, "date");
        var result = appointmentRepository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichAppointment)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    public Map<String, Object> getRecords(int page, int limit) {
        requireSpecialist();
        var pageable = PageUtils.toPageRequest(page, limit, "-createdAt");
        var result = screeningRecordRepository.findAll(pageable);
        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichScreeningRecord)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    private Map<String, Object> enrichAppointment(Appointment appointment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", appointment.getId());
        row.put("displayCode", appointment.getCode());
        row.put("appointmentDate", appointment.getDate());
        row.put("appointmentTime", appointment.getTime());
        row.put("date", appointment.getDate());
        row.put("time", appointment.getTime());
        row.put("status", appointment.getStatus());
        row.put("notes", appointment.getNotes());
        row.put("donorUuid", appointment.getDonorId());

        donorRepository.findById(appointment.getDonorId()).ifPresent(donor -> enrichDonorFields(row, donor));
        return row;
    }

    private Map<String, Object> enrichScreeningRecord(ScreeningRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("donorId", record.getDonorId());
        row.put("status", record.getStatus());
        row.put("screeningDate", record.getScreeningDate());
        row.put("screeningTime", record.getScreeningTime());
        row.put("personalInfo", record.getPersonalInfo());
        row.put("contactInfo", record.getContactInfo());
        row.put("identification", record.getIdentification());
        row.put("physicalInfo", record.getPhysicalInfo());
        row.put("medicalHistory", record.getMedicalHistory());
        row.put("lifestyle", record.getLifestyle());
        row.put("vitals", record.getVitals());
        row.put("eligibilityResult", record.getEligibilityResult());
        row.put("notes", record.getNotes());
        row.put("createdAt", record.getCreatedAt());
        row.put("updatedAt", record.getUpdatedAt());

        if (record.getBloodGroup() != null) {
            row.put("bloodGroup", record.getBloodGroup().getValue());
        }

        donorRepository.findById(record.getDonorId()).ifPresent(donor -> {
            row.put("donorDisplayCode", donor.getDisplayCode());
            row.put("donorName", donor.getFirstName() + " " + donor.getLastName());
            if (donor.getUser() != null) {
                row.put("donorPhone", donor.getUser().getPhone());
                row.put("donorEmail", donor.getUser().getEmail());
            }
            if (!row.containsKey("bloodGroup") && donor.getBloodGroup() != null) {
                row.put("bloodGroup", donor.getBloodGroup().getValue());
            }
        });

        if (record.getStaffId() != null) {
            staffRepository.findByUserId(record.getStaffId()).ifPresent(staff ->
                    row.put("staffName", staff.getName()));
        }
        if (record.getSpecialistId() != null) {
            staffRepository.findByUserId(record.getSpecialistId()).ifPresent(staff ->
                    row.put("screenedBy", staff.getName()));
        }

        if (record.getVitals() != null) {
            copyIfPresent(record.getVitals(), row, "hemoglobin", "hemoglobinLevel");
            copyIfPresent(record.getVitals(), row, "bloodPressure", "bp");
            copyIfPresent(record.getVitals(), row, "pulse", "heartRate");
            copyIfPresent(record.getVitals(), row, "temperature", "temp");
        }
        if (record.getPhysicalInfo() != null) {
            copyIfPresent(record.getPhysicalInfo(), row, "weight");
            copyIfPresent(record.getPhysicalInfo(), row, "gender");
        }
        if (record.getMedicalHistory() != null) {
            copyIfPresent(record.getMedicalHistory(), row, "allergies");
            copyIfPresent(record.getMedicalHistory(), row, "medications", "currentMedications");
            copyIfPresent(record.getMedicalHistory(), row, "medicalHistory", "conditions");
        }

        return row;
    }

    private void enrichDonorFields(Map<String, Object> row, Donor donor) {
        row.put("donorId", donor.getDisplayCode());
        row.put("donorName", donor.getFirstName() + " " + donor.getLastName());
        row.put("donorStatus", donor.getStatus());
        row.put("bloodGroup", donor.getBloodGroup() != null ? donor.getBloodGroup().getValue() : null);
        int donations = donor.getTotalDonations() != null ? donor.getTotalDonations() : 0;
        row.put("type", donations > 0 ? "Regular Donor" : "First Time Donor");
        if (donor.getUser() != null) {
            row.put("phone", donor.getUser().getPhone());
            row.put("email", donor.getUser().getEmail());
        }
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target,
                                      String sourceKey, String... targetKeys) {
        if (source == null || !source.containsKey(sourceKey) || source.get(sourceKey) == null) {
            return;
        }
        String value = String.valueOf(source.get(sourceKey));
        target.put(sourceKey, value);
        for (String key : targetKeys) {
            target.put(key, value);
        }
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        copyIfPresent(source, target, key, key);
    }

    @Transactional
    public ScreeningRecord startSession(ScreeningRecord screening) {
        requireSpecialist();
        screening.setSpecialistId(SecurityUtils.getCurrentUserId());
        return screeningService.createScreening(screening);
    }

    @Transactional
    public ScreeningRecord updateSession(UUID id, ScreeningRecord updates) {
        requireSpecialist();
        return screeningService.updateScreening(id, updates);
    }

    @Transactional
    public Map<String, Object> listPendingTests(int page, int limit) {
        requireSpecialist();
        return testingService.listTests(TestOverallStatus.Pending, page, limit, "-createdAt");
    }

    private void requireSpecialist() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"specialist".equalsIgnoreCase(role) && !"admin".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Specialist required", HttpStatus.FORBIDDEN);
        }
    }
}
