package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Urgency;
import com.bloodbank.bloodbank.util.BloodBankUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private static final Map<BloodProductType, String> BLOOD_TYPE_NAMES = Map.of(
            BloodProductType.WB, "Whole Blood",
            BloodProductType.RBC, "Red Blood Cells",
            BloodProductType.PLS, "Plasma",
            BloodProductType.PLT, "Platelets",
            BloodProductType.CRYO, "Cryoprecipitate"
    );

    private static final Map<BloodProductType, Integer> DEFAULT_VOLUMES = Map.of(
            BloodProductType.WB, 450,
            BloodProductType.RBC, 450,
            BloodProductType.PLS, 600,
            BloodProductType.PLT, 300,
            BloodProductType.CRYO, 450
    );

    public List<Map<String, Object>> getBloodTypes() {
        return Arrays.stream(BloodProductType.values())
                .map(type -> Map.<String, Object>of(
                        "code", type.getValue(),
                        "name", BLOOD_TYPE_NAMES.get(type),
                        "defaultVolumeMl", DEFAULT_VOLUMES.get(type),
                        "shelfLifeDays", BloodBankUtils.getShelfLifeDays(type)
                ))
                .toList();
    }

    public List<Map<String, String>> getBloodGroups() {
        return Arrays.stream(BloodGroup.values())
                .map(group -> Map.of("code", group.name(), "name", group.getValue()))
                .toList();
    }

    public List<Map<String, String>> getRegions() {
        return List.of(
                "Greater Accra", "Ashanti", "Western", "Central", "Eastern",
                "Northern", "Upper East", "Upper West", "Volta", "Bono",
                "Bono East", "Ahafo", "Western North", "Oti", "Savannah", "North East"
        ).stream()
                .map(name -> Map.of("name", name, "country", "Ghana"))
                .toList();
    }

    public List<Map<String, Object>> getUrgencyLevels() {
        return List.of(
                Map.of("code", Urgency.Critical.name(), "name", "Critical", "priority", 1, "slaHours", 4),
                Map.of("code", Urgency.High.name(), "name", "High", "priority", 2, "slaHours", 12),
                Map.of("code", Urgency.Medium.name(), "name", "Medium", "priority", 3, "slaHours", 24),
                Map.of("code", Urgency.Low.name(), "name", "Low", "priority", 4, "slaHours", 72)
        );
    }
}
