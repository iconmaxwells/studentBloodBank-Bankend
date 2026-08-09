package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "appointment_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Column(name = "requested_time")
    private LocalTime requestedTime;

    private String location;

    @Column(name = "blood_bank_id")
    private UUID bloodBankId;

    @Column(name = "donation_type")
    private String donationType;

    @Enumerated(EnumType.STRING)
    private AppointmentRequestStatus status = AppointmentRequestStatus.Pending;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "staff_response")
    private String staffResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
