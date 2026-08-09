package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.*;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentRequestRepository appointmentRequestRepository;
    private final DonorRepository donorRepository;
    private final DisplayCodeService displayCodeService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Map<String, Object> listAppointments(UUID donorId, int page, int limit, String sort) {
        UUID scopedDonorId = resolveDonorScope(donorId);
        PageRequest pageable = PageUtils.toPageRequest(page, limit, sort);
        Page<Appointment> result = scopedDonorId != null
                ? appointmentRepository.findByDonorId(scopedDonorId, pageable)
                : appointmentRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    @Transactional(readOnly = true)
    public Appointment getById(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment"));
        authorizeAppointmentAccess(appointment);
        return appointment;
    }

    public Appointment createAppointment(Appointment appointment) {
        if (!"donor".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Donor only", HttpStatus.FORBIDDEN);
        }
        Donor donor = donorRepository.findByUserId(SecurityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        appointment.setDonorId(donor.getId());
        appointment.setCode(displayCodeService.nextCode(EntityType.APPOINTMENT));
        appointment.setStatus(AppointmentStatus.Pending);
        return appointmentRepository.save(appointment);
    }

    public Appointment updateAppointment(UUID id, Appointment updates) {
        Appointment appointment = getById(id);
        if (updates.getDate() != null) appointment.setDate(updates.getDate());
        if (updates.getTime() != null) appointment.setTime(updates.getTime());
        if (updates.getBloodProductType() != null) appointment.setBloodProductType(updates.getBloodProductType());
        if (updates.getNotes() != null) appointment.setNotes(updates.getNotes());
        if (updates.getStatus() != null) appointment.setStatus(updates.getStatus());
        return appointmentRepository.save(appointment);
    }

    public Appointment cancelAppointment(UUID id) {
        Appointment appointment = getById(id);
        appointment.setStatus(AppointmentStatus.Cancelled);
        Appointment saved = appointmentRepository.save(appointment);
        donorRepository.findById(appointment.getDonorId()).ifPresent(donor ->
                notificationService.notifyUser(donor.getUser().getId(), NotificationType.info,
                        "Appointment cancelled", "Your appointment " + appointment.getCode() + " was cancelled.",
                        "appointment", appointment.getId().toString()));
        return saved;
    }

    public Appointment confirmAppointment(UUID id) {
        requireStaff();
        Appointment appointment = getById(id);
        appointment.setStatus(AppointmentStatus.Confirmed);
        Appointment saved = appointmentRepository.save(appointment);
        donorRepository.findById(appointment.getDonorId()).ifPresent(donor ->
                notificationService.notifyUser(donor.getUser().getId(), NotificationType.success,
                        "Appointment confirmed", "Your appointment " + appointment.getCode() + " is confirmed.",
                        "appointment", appointment.getId().toString()));
        return saved;
    }

    public Appointment completeScheduledAppointment(UUID appointmentId) {
        requireScreeningStaff();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment"));
        appointment.setStatus(AppointmentStatus.Completed);
        return appointmentRepository.save(appointment);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listAppointmentRequests(AppointmentRequestStatus status, int page, int limit) {
        requireStaff();
        PageRequest pageable = PageUtils.toPageRequest(page, limit, "-createdAt");
        Page<AppointmentRequest> result = status != null
                ? appointmentRequestRepository.findByStatus(status, pageable)
                : appointmentRequestRepository.findAll(pageable);
        return Map.of("items", result.getContent(), "meta", PageUtils.toMeta(result, page, limit));
    }

    public Appointment approveAppointmentRequest(UUID id, String staffResponse) {
        requireStaff();
        AppointmentRequest request = appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request"));
        if (request.getStatus() != AppointmentRequestStatus.Pending) {
            throw new BusinessRuleException("INVALID_STATUS", "Request is not pending");
        }
        request.setStatus(AppointmentRequestStatus.Approved);
        request.setStaffResponse(staffResponse);
        appointmentRequestRepository.save(request);

        Appointment appointment = Appointment.builder()
                .donorId(request.getDonorId())
                .bloodBankId(request.getBloodBankId())
                .date(request.getRequestedDate())
                .time(request.getRequestedTime())
                .notes(request.getNotes())
                .code(displayCodeService.nextCode(EntityType.APPOINTMENT))
                .status(AppointmentStatus.Scheduled)
                .build();
        Appointment saved = appointmentRepository.save(appointment);

        donorRepository.findById(request.getDonorId()).ifPresent(donor ->
                notificationService.notifyUser(donor.getUser().getId(), NotificationType.success,
                        "Appointment approved", staffResponse != null ? staffResponse : "Your appointment request was approved.",
                        "appointment", saved.getId().toString()));
        return saved;
    }

    public AppointmentRequest rejectAppointmentRequest(UUID id, String staffResponse) {
        requireStaff();
        AppointmentRequest request = appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment request"));
        if (request.getStatus() != AppointmentRequestStatus.Pending) {
            throw new BusinessRuleException("INVALID_STATUS", "Request is not pending");
        }
        request.setStatus(AppointmentRequestStatus.Rejected);
        request.setStaffResponse(staffResponse);
        AppointmentRequest saved = appointmentRequestRepository.save(request);

        donorRepository.findById(request.getDonorId()).ifPresent(donor ->
                notificationService.notifyUser(donor.getUser().getId(), NotificationType.warning,
                        "Appointment rejected", staffResponse != null ? staffResponse : "Your appointment request was rejected.",
                        "appointment_request", saved.getId().toString()));
        return saved;
    }

    private UUID resolveDonorScope(UUID donorId) {
        if ("donor".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            return donorRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .map(Donor::getId)
                    .orElseThrow(() -> new ResourceNotFoundException("Donor"));
        }
        if (!isStaffOrAdmin(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
        return donorId;
    }

    private void authorizeAppointmentAccess(Appointment appointment) {
        if ("donor".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            Donor donor = donorRepository.findByUserId(SecurityUtils.getCurrentUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Donor"));
            if (!appointment.getDonorId().equals(donor.getId())) {
                throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
        } else if (!isStaffOrAdmin(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Staff required", HttpStatus.FORBIDDEN);
        }
    }

    private void requireScreeningStaff() {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"admin".equalsIgnoreCase(role) && !"staff".equalsIgnoreCase(role)
                && !"specialist".equalsIgnoreCase(role)) {
            throw new ApiException("FORBIDDEN", "Specialist or staff required", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isStaffOrAdmin(String role) {
        return "admin".equalsIgnoreCase(role) || "staff".equalsIgnoreCase(role);
    }
}
