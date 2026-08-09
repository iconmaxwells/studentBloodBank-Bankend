package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EligibilityResult;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ScreeningStatus;
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
@Table(name = "screening_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "specialist_id")
    private UUID specialistId;

    @Column(name = "screening_date")
    private LocalDate screeningDate;

    @Column(name = "screening_time")
    private LocalTime screeningTime;

    @Enumerated(EnumType.STRING)
    private ScreeningStatus status = ScreeningStatus.In_Progress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personal_info", columnDefinition = "json")
    private Map<String, Object> personalInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contact_info", columnDefinition = "json")
    private Map<String, Object> contactInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> identification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "physical_info", columnDefinition = "json")
    private Map<String, Object> physicalInfo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "medical_history", columnDefinition = "json")
    private Map<String, Object> medicalHistory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> lifestyle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> vitals;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_result")
    private EligibilityResult eligibilityResult;

    @Column(name = "deferral_reason")
    private String deferralReason;

    @Column(name = "deferral_until")
    private LocalDate deferralUntil;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

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
