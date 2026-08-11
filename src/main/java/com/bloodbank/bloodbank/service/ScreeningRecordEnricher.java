package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.ScreeningRecord;
import com.bloodbank.bloodbank.repository.DonorRepository;
import com.bloodbank.bloodbank.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScreeningRecordEnricher {

    private final DonorRepository donorRepository;
    private final StaffRepository staffRepository;

    public Map<String, Object> enrich(ScreeningRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("donorId", record.getDonorId());
        row.put("staffId", record.getStaffId());
        row.put("specialistId", record.getSpecialistId());
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

        donorRepository.findById(record.getDonorId()).ifPresent(donor -> enrichDonorFields(row, donor));

        applyScreenerNames(record, row);

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

    private void applyScreenerNames(ScreeningRecord record, Map<String, Object> row) {
        if (record.getSpecialistId() != null) {
            staffRepository.findByUserId(record.getSpecialistId()).ifPresent(specialist -> {
                row.put("specialistName", specialist.getName());
                row.put("screenedBy", specialist.getName());
                row.put("staffName", specialist.getName());
            });
        } else if (record.getStaffId() != null) {
            staffRepository.findByUserId(record.getStaffId()).ifPresent(staff -> {
                row.put("staffName", staff.getName());
                row.put("screenedBy", staff.getName());
            });
        }

        if (record.getStaffId() != null
                && record.getSpecialistId() != null
                && !record.getStaffId().equals(record.getSpecialistId())) {
            staffRepository.findByUserId(record.getStaffId()).ifPresent(staff ->
                    row.put("demographicStaffName", staff.getName()));
        }
    }

    private void enrichDonorFields(Map<String, Object> row, Donor donor) {
        row.put("donorDisplayCode", donor.getDisplayCode());
        row.put("donorName", donor.getFirstName() + " " + donor.getLastName());
        if (donor.getUser() != null) {
            row.put("donorPhone", donor.getUser().getPhone());
            row.put("donorEmail", donor.getUser().getEmail());
        }
        if (!row.containsKey("bloodGroup") && donor.getBloodGroup() != null) {
            row.put("bloodGroup", donor.getBloodGroup().getValue());
        }
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target,
                                      String sourceKey, String targetKey) {
        Object value = source.get(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        copyIfPresent(source, target, key, key);
    }
}
