package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.Collection;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.RequestStatus;
import com.bloodbank.bloodbank.exception.ApiException;
import com.bloodbank.bloodbank.repository.*;
import com.bloodbank.bloodbank.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final DonorRepository donorRepository;
    private final CollectionRepository collectionRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final InventoryService inventoryService;

    public Map<String, Object> getStats() {
        requireAdmin();
        long totalDonors = donorRepository.count();
        long totalCollections = collectionRepository.count();
        long pendingRequests = bloodRequestRepository.findByStatus(RequestStatus.Pending,
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        List<Map<String, Object>> alerts = inventoryService.getAlerts();

        return Map.of(
                "totalDonors", totalDonors,
                "totalCollections", totalCollections,
                "pendingRequests", pendingRequests,
                "inventoryAlerts", alerts.size(),
                "criticalAlerts", alerts.stream().filter(a -> "critical".equals(a.get("type"))).count()
        );
    }

    public Map<String, Object> getCharts() {
        requireAdmin();
        List<Collection> collections = collectionRepository.findAll();
        YearMonth current = YearMonth.now();

        List<String> monthLabels = new ArrayList<>();
        List<Long> collectionCounts = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            monthLabels.add(month.toString());
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            long count = collections.stream()
                    .filter(c -> c.getCollectionDate() != null
                            && !c.getCollectionDate().isBefore(start)
                            && !c.getCollectionDate().isAfter(end))
                    .count();
            collectionCounts.add(count);
        }

        Map<String, Long> bloodGroupDistribution = collections.stream()
                .filter(c -> c.getBloodGroup() != null)
                .collect(Collectors.groupingBy(c -> c.getBloodGroup().getValue(), Collectors.counting()));

        return Map.of(
                "monthlyCollections", Map.of("labels", monthLabels, "data", collectionCounts),
                "bloodGroupDistribution", bloodGroupDistribution
        );
    }

    public List<Map<String, Object>> getMonitoringInventory() {
        requireAdmin();
        return inventoryService.getSummary();
    }

    public List<Map<String, Object>> getMonitoringAlerts() {
        requireAdmin();
        return inventoryService.getAlerts();
    }

    private void requireAdmin() {
        if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            throw new ApiException("FORBIDDEN", "Admin only", HttpStatus.FORBIDDEN);
        }
    }
}
