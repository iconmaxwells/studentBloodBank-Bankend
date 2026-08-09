package com.bloodbank.bloodbank.entity;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.RewardLevel;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "donor_rewards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonorReward {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "donor_id", unique = true, nullable = false)
    private UUID donorId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RewardLevel level = RewardLevel.Bronze;

    @Builder.Default
    private Integer points = 0;

    @Builder.Default
    @Column(name = "total_donations")
    private Integer totalDonations = 0;

    @Builder.Default
    private Integer streak = 0;

    @Builder.Default
    @Column(name = "total_earnings")
    private Double totalEarnings = 0.0;

    @Builder.Default
    @Column(name = "pending_payment")
    private Double pendingPayment = 0.0;

    @Builder.Default
    @Column(name = "total_redeemed")
    private Double totalRedeemed = 0.0;
}
