package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalCapacity;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "hospitals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE hospitals SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Hospital {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String displayCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(name = "registration_number", unique = true)
    private String registrationNumber;

    private String location;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;

    @Column(name = "emergency_phone")
    private String emergencyPhone;

    private String email;
    private String website;

    @Enumerated(EnumType.STRING)
    private HospitalCapacity capacity;

    private Integer beds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> departments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "primary_contact", columnDefinition = "json")
    private Map<String, Object> primaryContact;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blood_bank_coordinator", columnDefinition = "json")
    private Map<String, Object> bloodBankCoordinator;

    @Column(name = "operating_hours")
    private String operatingHours;

    private String accreditation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Map<String, Object>> licenses;

    @Enumerated(EnumType.STRING)
    private HospitalStatus status = HospitalStatus.Active;

    @Column(name = "total_requests")
    private Integer totalRequests = 0;

    @Column(name = "pending_requests")
    private Integer pendingRequests = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalizeCounters();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        normalizeCounters();
    }

    @PostLoad
    private void normalizeCounters() {
        if (totalRequests == null) {
            totalRequests = 0;
        }
        if (pendingRequests == null) {
            pendingRequests = 0;
        }
    }
}
