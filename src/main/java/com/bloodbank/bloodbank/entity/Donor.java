package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.CompensationMethod;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.DonorStatus;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Gender;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.IdType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "donors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE donors SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Donor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String displayCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private BloodGroup bloodGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type")
    private IdType idType;

    @Column(name = "id_number", unique = true)
    private String idNumber;

    private String address;
    private String city;
    private String region;

    @Column(name = "postal_code")
    private String postalCode;

    private Double weight;
    private Double height;

    @Enumerated(EnumType.STRING)
    private DonorStatus status = DonorStatus.Pending_Screening;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(name = "next_eligible_date")
    private LocalDate nextEligibleDate;

    @Column(name = "total_donations")
    private Integer totalDonations = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "emergency_contact", columnDefinition = "json")
    private Map<String, Object> emergencyContact;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "medical_history", columnDefinition = "json")
    private Map<String, Object> medicalHistory;

    @Column(name = "registered_date")
    private LocalDate registeredDate;

    /** When true, the donor donates voluntarily and does not receive monetary compensation. */
    @Column(name = "is_voluntary")
    @Builder.Default
    private Boolean isVoluntary = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_payout_method")
    private CompensationMethod preferredPayoutMethod;

    @Column(name = "payout_phone_number")
    private String payoutPhoneNumber;

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
        if (registeredDate == null) {
            registeredDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
