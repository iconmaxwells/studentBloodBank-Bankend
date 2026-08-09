package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.SystemSettings;
import com.bloodbank.bloodbank.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final SystemSettingsRepository systemSettingsRepository;

    public SystemSettings getSettings() {
        SystemSettings settings = systemSettingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> systemSettingsRepository.save(defaultSettings()));
        return applyDefaults(settings);
    }

    public SystemSettings updateSettings(SystemSettings updates) {
        SystemSettings current = getSettings();
        if (updates.getBloodBankName() != null) current.setBloodBankName(updates.getBloodBankName());
        if (updates.getContactEmail() != null) current.setContactEmail(updates.getContactEmail());
        if (updates.getContactPhone() != null) current.setContactPhone(updates.getContactPhone());
        if (updates.getAddress() != null) current.setAddress(updates.getAddress());
        if (updates.getDonorCompensationDefault() != null) current.setDonorCompensationDefault(updates.getDonorCompensationDefault());
        if (updates.getMinDonationIntervalDays() != null) current.setMinDonationIntervalDays(updates.getMinDonationIntervalDays());
        if (updates.getMinAge() != null) current.setMinAge(updates.getMinAge());
        if (updates.getMaxAge() != null) current.setMaxAge(updates.getMaxAge());
        if (updates.getMinWeightKg() != null) current.setMinWeightKg(updates.getMinWeightKg());
        if (updates.getNotificationPreferences() != null) current.setNotificationPreferences(updates.getNotificationPreferences());
        if (updates.getSecuritySettings() != null) current.setSecuritySettings(updates.getSecuritySettings());
        if (updates.getInventoryThresholds() != null) current.setInventoryThresholds(updates.getInventoryThresholds());
        return systemSettingsRepository.save(applyDefaults(current));
    }

    private SystemSettings defaultSettings() {
        return SystemSettings.builder()
                .bloodBankName("National Blood Bank")
                .contactEmail("contact@bloodbank.com")
                .contactPhone("+233000000000")
                .address("Accra, Ghana")
                .donorCompensationDefault(75.0)
                .minDonationIntervalDays(90)
                .minAge(18)
                .maxAge(65)
                .minWeightKg(50.0)
                .build();
    }

    private SystemSettings applyDefaults(SystemSettings settings) {
        if (settings.getDonorCompensationDefault() == null) settings.setDonorCompensationDefault(75.0);
        if (settings.getMinDonationIntervalDays() == null) settings.setMinDonationIntervalDays(90);
        if (settings.getMinAge() == null) settings.setMinAge(18);
        if (settings.getMaxAge() == null) settings.setMaxAge(65);
        if (settings.getMinWeightKg() == null) settings.setMinWeightKg(50.0);
        return settings;
    }
}
