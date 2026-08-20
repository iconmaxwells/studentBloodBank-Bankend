package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.CompensationMethod;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "earnings_withdrawals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EarningsWithdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private CompensationMethod method;

    private String destination;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.Pending;

    @Column(name = "reference_code")
    private String referenceCode;

    @Column(name = "payments_count")
    private Integer paymentsCount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
