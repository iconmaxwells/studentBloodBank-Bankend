package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.Patient;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.HospitalRepository;
import com.bloodbank.bloodbank.repository.PatientRepository;
import com.bloodbank.bloodbank.util.PageUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final HospitalRepository hospitalRepository;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public Map<String, Object> listPatients(UUID hospitalId, int page, int limit, String sort) {
        String role = SecurityUtils.getCurrentUserRole();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<Patient> result;

        if ("hospital".equalsIgnoreCase(role)) {
            UUID scopedHospitalId = resolveHospitalScope(null);
            result = patientRepository.findByHospitalId(scopedHospitalId, pageable);
        } else if (isStaffOrAdmin(role)) {
            result = hospitalId != null
                    ? patientRepository.findByHospitalId(hospitalId, pageable)
                    : patientRepository.findAll(pageable);
        } else {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }

        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public Patient getById(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient"));
        authorizePatientAccess(patient);
        return patient;
    }

    public Patient createPatient(Patient patient) {
        requireHospitalOrStaff();
        patient.setHospitalId(resolveHospitalScope(patient.getHospitalId()));
        Patient saved = patientRepository.save(patient);
        activityLogService.log(ActionType.create, "create_patient", "Created patient: " + saved.getName(),
                "patient", null, null, saved.getHospitalId(), null, null);
        return saved;
    }

    public Patient updatePatient(UUID id, Patient updates) {
        Patient patient = getById(id);
        requireHospitalOrStaff();
        if (updates.getName() != null) patient.setName(updates.getName());
        if (updates.getBloodGroup() != null) patient.setBloodGroup(updates.getBloodGroup());
        if (updates.getAge() != null) patient.setAge(updates.getAge());
        if (updates.getGender() != null) patient.setGender(updates.getGender());
        if (updates.getDiagnosis() != null) patient.setDiagnosis(updates.getDiagnosis());
        if (updates.getRequiredUnits() != null) patient.setRequiredUnits(updates.getRequiredUnits());
        if (updates.getStatus() != null) patient.setStatus(updates.getStatus());
        return patientRepository.save(patient);
    }

    public void deletePatient(UUID id) {
        Patient patient = getById(id);
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"hospital".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
        patientRepository.delete(patient);
        activityLogService.log(ActionType.delete, "delete_patient", "Deleted patient: " + patient.getName(),
                "patient", null, null, patient.getHospitalId(), null, null);
    }

    private UUID resolveHospitalScope(UUID hospitalId) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
            return hospital.getId();
        }
        if (hospitalId == null) {
            throw new ApiException("VALIDATION_ERROR", "hospitalId is required", HttpStatus.BAD_REQUEST);
        }
        return hospitalId;
    }

    private void authorizePatientAccess(Patient patient) {
        String role = SecurityUtils.getCurrentUserRole();
        if ("hospital".equalsIgnoreCase(role)) {
            Hospital hospital = hospitalRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hospital"));
            if (!patient.getHospitalId().equals(hospital.getId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireHospitalOrStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"hospital".equalsIgnoreCase(role) && !isStaffOrAdmin(role)) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role);
    }
}
