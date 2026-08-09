package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.BloodBank;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.ActionType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.BloodBankStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.exception.ResourceNotFoundException;
import com.bloodbank.bloodbank.repository.BloodBankRepository;
import com.bloodbank.bloodbank.util.BloodBankUtils;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BloodBankService {

    private final BloodBankRepository bloodBankRepository;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public List<BloodBank> listBloodBanks() {
        return bloodBankRepository.findAll();
    }

    @Transactional(readOnly = true)
    public BloodBank getById(UUID id) {
        return bloodBankRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank"));
    }

    @Transactional(readOnly = true)
    public BloodBank getByDisplayCode(String displayCode) {
        return bloodBankRepository.findByDisplayCode(displayCode)
                .orElseThrow(() -> new ResourceNotFoundException("Blood bank"));
    }

    public BloodBank createBloodBank(BloodBank bloodBank) {
        requireAdmin();
        if (bloodBank.getDisplayCode() == null) {
            bloodBank.setDisplayCode("BB" + String.format("%03d", bloodBankRepository.count() + 1));
        }
        if (bloodBank.getStatus() == null) {
            bloodBank.setStatus(BloodBankStatus.Open);
        }
        BloodBank saved = bloodBankRepository.save(bloodBank);
        activityLogService.log(ActionType.create, "create_blood_bank", "Created blood bank: " + saved.getName(),
                "blood_bank", null, null, null, null, null);
        return saved;
    }

    public BloodBank updateBloodBank(UUID id, BloodBank updates) {
        requireAdmin();
        BloodBank bloodBank = getById(id);
        applyUpdates(bloodBank, updates);
        BloodBank saved = bloodBankRepository.save(bloodBank);
        activityLogService.log(ActionType.update, "update_blood_bank", "Updated blood bank: " + saved.getName(),
                "blood_bank", null, null, null, null, null);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findNearby(double lat, double lng, double radiusKm) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (BloodBank bank : bloodBankRepository.findAll()) {
            if (bank.getLatitude() == null || bank.getLongitude() == null) {
                continue;
            }
            double distance = BloodBankUtils.haversineKm(lat, lng, bank.getLatitude(), bank.getLongitude());
            if (distance <= radiusKm) {
                results.add(Map.of(
                        "bloodBank", bank,
                        "distanceKm", Math.round(distance * 100.0) / 100.0
                ));
            }
        }
        results.sort(Comparator.comparingDouble(m -> (Double) m.get("distanceKm")));
        return results;
    }

    private void applyUpdates(BloodBank bloodBank, BloodBank updates) {
        if (updates.getName() != null) bloodBank.setName(updates.getName());
        if (updates.getAddress() != null) bloodBank.setAddress(updates.getAddress());
        if (updates.getLocation() != null) bloodBank.setLocation(updates.getLocation());
        if (updates.getLatitude() != null) bloodBank.setLatitude(updates.getLatitude());
        if (updates.getLongitude() != null) bloodBank.setLongitude(updates.getLongitude());
        if (updates.getPhone() != null) bloodBank.setPhone(updates.getPhone());
        if (updates.getEmail() != null) bloodBank.setEmail(updates.getEmail());
        if (updates.getOperatingHours() != null) bloodBank.setOperatingHours(updates.getOperatingHours());
        if (updates.getAvailableServices() != null) bloodBank.setAvailableServices(updates.getAvailableServices());
        if (updates.getStatus() != null) bloodBank.setStatus(updates.getStatus());
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }
}
