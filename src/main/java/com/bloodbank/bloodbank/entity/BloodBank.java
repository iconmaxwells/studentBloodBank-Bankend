package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.BloodBankStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "blood_banks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloodBank {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String displayCode;

    @Column(nullable = false)
    private String name;

    private String address;
    private String location;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String email;

    @Column(name = "operating_hours")
    private String operatingHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "available_services", columnDefinition = "json")
    private List<String> availableServices;

    @Enumerated(EnumType.STRING)
    private BloodBankStatus status = BloodBankStatus.Open;

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
