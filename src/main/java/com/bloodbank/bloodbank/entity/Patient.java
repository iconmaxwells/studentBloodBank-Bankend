package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Gender;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.PatientStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE patients SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String diagnosis;

    @Column(name = "required_units")
    private Integer requiredUnits;

    @Enumerated(EnumType.STRING)
    private PatientStatus status = PatientStatus.Stable;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (admissionDate == null) {
            admissionDate = LocalDate.now();
        }
    }
}
