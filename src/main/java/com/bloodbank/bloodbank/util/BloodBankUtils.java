package com.bloodbank.bloodbank.util;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RewardLevel;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BloodBankUtils {

    private static final Map<BloodProductType, Integer> SHELF_LIFE_DAYS = Map.of(
            BloodProductType.WB, 35,
            BloodProductType.RBC, 42,
            BloodProductType.PLS, 365,
            BloodProductType.PLT, 5,
            BloodProductType.CRYO, 365
    );

    private BloodBankUtils() {}

    public static int getShelfLifeDays(BloodProductType productType) {
        return SHELF_LIFE_DAYS.getOrDefault(productType, 35);
    }

    public static LocalDate calculateExpiryDate(LocalDate collectionDate, BloodProductType productType) {
        return collectionDate.plusDays(getShelfLifeDays(productType));
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    public static Set<BloodGroup> compatibleGroups(BloodGroup needed) {
        return switch (needed) {
            case O_NEGATIVE -> EnumSet.of(BloodGroup.O_NEGATIVE);
            case O_POSITIVE -> EnumSet.of(BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE);
            case A_NEGATIVE -> EnumSet.of(BloodGroup.A_NEGATIVE, BloodGroup.O_NEGATIVE);
            case A_POSITIVE -> EnumSet.of(BloodGroup.A_POSITIVE, BloodGroup.A_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE);
            case B_NEGATIVE -> EnumSet.of(BloodGroup.B_NEGATIVE, BloodGroup.O_NEGATIVE);
            case B_POSITIVE -> EnumSet.of(BloodGroup.B_POSITIVE, BloodGroup.B_NEGATIVE, BloodGroup.O_POSITIVE, BloodGroup.O_NEGATIVE);
            case AB_NEGATIVE -> EnumSet.of(BloodGroup.AB_NEGATIVE, BloodGroup.A_NEGATIVE, BloodGroup.B_NEGATIVE, BloodGroup.O_NEGATIVE);
            case AB_POSITIVE -> EnumSet.allOf(BloodGroup.class);
        };
    }

    public static RewardLevel calculateRewardLevel(int points) {
        if (points >= 5000) return RewardLevel.Diamond;
        if (points >= 2000) return RewardLevel.Platinum;
        if (points >= 1000) return RewardLevel.Gold;
        if (points >= 500) return RewardLevel.Silver;
        return RewardLevel.Bronze;
    }

    public static List<Map<String, Object>> defaultTestPanel() {
        return List.of(
                Map.of("name", "HIV", "result", "", "status", "In_Progress"),
                Map.of("name", "Hepatitis B", "result", "", "status", "In_Progress"),
                Map.of("name", "Hepatitis C", "result", "", "status", "In_Progress"),
                Map.of("name", "Syphilis", "result", "", "status", "In_Progress"),
                Map.of("name", "Blood Type Confirmation", "result", "", "status", "In_Progress")
        );
    }
}
