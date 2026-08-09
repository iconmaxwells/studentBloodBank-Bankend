package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.CompensationMethod;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compensation_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompensationPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    @Column(name = "collection_id")
    private UUID collectionId;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private CompensationMethod method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.Pending;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
