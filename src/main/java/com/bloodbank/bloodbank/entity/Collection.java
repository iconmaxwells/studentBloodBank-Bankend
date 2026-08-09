package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CollectionStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "collections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String displayCode;

    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_product_type")
    private BloodProductType bloodProductType;

    @Column(name = "volume_ml")
    private Integer volumeMl;

    @Column(name = "bag_number")
    private String bagNumber;

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(name = "collection_time")
    private LocalTime collectionTime;

    private String location;

    @Column(name = "blood_bank_id")
    private UUID bloodBankId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pre_screening_vitals", columnDefinition = "json")
    private Map<String, Object> preScreeningVitals;

    private String anticoagulant;

    @Column(name = "storage_location")
    private String storageLocation;

    @Enumerated(EnumType.STRING)
    private CollectionStatus status = CollectionStatus.In_Progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_result")
    private TestOverallStatus testResult = TestOverallStatus.Pending;

    @Column(name = "compensation_amount")
    private Double compensationAmount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
