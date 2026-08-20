package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.SupplyRequestStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Urgency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "supply_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplyRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String displayCode;

    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_product_type")
    private BloodProductType bloodProductType;

    @Column(name = "units_requested")
    private Integer unitsRequested;

    @Enumerated(EnumType.STRING)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    private SupplyRequestStatus status = SupplyRequestStatus.Submitted;

    @Column(name = "supplier_blood_bank_id")
    private UUID supplierBloodBankId;

    @Column(name = "supplier_blood_bank_name")
    private String supplierBloodBankName;

    @Column(name = "required_by")
    private LocalDate requiredBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "current_units")
    private Integer currentUnits;

    private Integer capacity;

    /** Set when delivered units have been added to local inventory (prevents double-counting). */
    @Column(name = "inventory_applied")
    @Builder.Default
    private Boolean inventoryApplied = false;

    @Column(name = "requested_by_id")
    private UUID requestedById;

    @Column(name = "requested_by_name")
    private String requestedByName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_up_notes", columnDefinition = "json")
    @Builder.Default
    private List<Map<String, Object>> followUpNotes = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (followUpNotes == null) {
            followUpNotes = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
