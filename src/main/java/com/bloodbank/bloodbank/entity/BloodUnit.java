package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "blood_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloodUnit {
    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "collection_id")
    private UUID collectionId;

    @Column(name = "donor_id")
    private UUID donorId;

    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_product_type")
    private BloodProductType bloodProductType;

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    private UnitStatus status = UnitStatus.Available;

    private String location;

    @Column(name = "reserved_for_request_id")
    private UUID reservedForRequestId;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "discarded_at")
    private LocalDateTime discardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
