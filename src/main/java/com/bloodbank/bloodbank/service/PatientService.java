package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.Patient;
import com.bloodbank.bloodbank.entity.BloodRequest;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PatientStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Urgency;
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

import java.util.LinkedHashMap;
import java.util.List;
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

        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::enrichPatient)
                .toList();
        return Map.of("items", items, "meta", PageUtils.toMeta(result, page, limit));
    }

    /**
     * Creates or updates a patient record when a hospital submits a blood request.
     */
    public Patient syncFromBloodRequest(BloodRequest request) {
        if (request.getPatientName() == null || request.getPatientName().isBlank()) {
            return null;
        }

        UUID hospitalId = request.getHospitalId();
        String externalId = request.getPatientId() != null ? request.getPatientId().trim() : null;
        String name = request.getPatientName().trim();

        Patient patient = null;
        if (externalId != null && !externalId.isBlank()) {
            patient = patientRepository.findFirstByHospitalIdAndExternalId(hospitalId, externalId).orElse(null);
        }
        if (patient == null) {
            patient = patientRepository.findFirstByHospitalIdAndNameIgnoreCase(hospitalId, name).orElse(null);
        }

        PatientStatus status = mapUrgencyToPatientStatus(request.getUrgency());
        int requestedUnits = request.getUnitsRequested() != null ? request.getUnitsRequested() : 0;

        if (patient == null) {
            patient = Patient.builder()
                    .hospitalId(hospitalId)
                    .externalId(externalId)
                    .name(name)
                    .bloodGroup(request.getBloodGroup())
                    .diagnosis(request.getDiagnosis())
                    .requiredUnits(requestedUnits)
                    .status(status)
                    .build();
            Patient saved = patientRepository.save(patient);
            activityLogService.log(ActionType.create, "create_patient_from_request",
                    "Created patient from blood request: " + saved.getName(),
                    "patient", null, null, saved.getHospitalId(), null, null);
            return saved;
        }

        patient.setExternalId(externalId != null && !externalId.isBlank() ? externalId : patient.getExternalId());
        patient.setName(name);
        if (request.getBloodGroup() != null) {
            patient.setBloodGroup(request.getBloodGroup());
        }
        if (request.getDiagnosis() != null && !request.getDiagnosis().isBlank()) {
            patient.setDiagnosis(request.getDiagnosis());
        }
        patient.setRequiredUnits(safeUnits(patient.getRequiredUnits()) + requestedUnits);
        patient.setStatus(status);
        return patientRepository.save(patient);
    }

    private Map<String, Object> enrichPatient(Patient patient) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", patient.getId());
        row.put("displayCode", "PAT-" + patient.getId().toString().substring(0, 8).toUpperCase());
        row.put("hospitalId", patient.getHospitalId());
        row.put("externalId", patient.getExternalId());
        row.put("name", patient.getName());
        row.put("bloodGroup", patient.getBloodGroup() != null ? patient.getBloodGroup().getValue() : null);
        row.put("age", patient.getAge());
        row.put("gender", patient.getGender());
        row.put("diagnosis", patient.getDiagnosis());
        row.put("requiredUnits", patient.getRequiredUnits());
        row.put("status", patient.getStatus());
        row.put("admissionDate", patient.getAdmissionDate());
        row.put("createdAt", patient.getCreatedAt());
        hospitalRepository.findById(patient.getHospitalId()).ifPresent(hospital -> {
            row.put("hospitalName", hospital.getName());
            row.put("hospital", hospital.getName());
        });
        return row;
    }

    private PatientStatus mapUrgencyToPatientStatus(Urgency urgency) {
        if (urgency == Urgency.Critical || urgency == Urgency.High) {
            return PatientStatus.Critical;
        }
        return PatientStatus.Stable;
    }

    private static int safeUnits(Integer value) {
        return value != null ? value : 0;
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
