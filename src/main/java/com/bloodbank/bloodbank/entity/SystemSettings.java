package com.bloodbank.bloodbank.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blood_bank_name")
    private String bloodBankName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Column(name = "donor_compensation_default")
    private Double donorCompensationDefault = 75.0;

    @Column(name = "hospital_service_charge_default")
    private Double hospitalServiceChargeDefault = 500.0;

    @Column(name = "min_donation_interval_days")
    private Integer minDonationIntervalDays = 90;

    @Column(name = "min_age")
    private Integer minAge = 18;

    @Column(name = "max_age")
    private Integer maxAge = 65;

    @Column(name = "min_weight_kg")
    private Double minWeightKg = 50.0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", columnDefinition = "json")
    private Map<String, Object> notificationPreferences;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_settings", columnDefinition = "json")
    private Map<String, Object> securitySettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "inventory_thresholds", columnDefinition = "json")
    private Map<String, Object> inventoryThresholds;
}
