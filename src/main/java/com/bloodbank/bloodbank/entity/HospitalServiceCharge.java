package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "hospital_service_charges",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hospital_id", "billing_period"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalServiceCharge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_code", unique = true, nullable = false)
    private String displayCode;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "billing_period", nullable = false)
    private String billingPeriod;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.Pending;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        if (issuedAt == null) {
            issuedAt = LocalDateTime.now();
        }
    }
}
