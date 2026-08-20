package com.bloodbank.bloodbank.job;

import com.bloodbank.bloodbank.entity.BloodUnit;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.UnitStatus;
import com.bloodbank.bloodbank.repository.BloodUnitRepository;
import com.bloodbank.bloodbank.service.NotificationService;
import com.bloodbank.bloodbank.websocket.LiveEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryExpiryJob {

    private final BloodUnitRepository bloodUnitRepository;
    private final NotificationService notificationService;
    private final LiveEventPublisher liveEventPublisher;

    @Scheduled(cron = "${app.jobs.expiry-cron:0 0 2 * * *}")
    @Transactional
    public void markExpiredUnits() {
        LocalDate today = LocalDate.now();
        List<BloodUnit> expired = bloodUnitRepository.findByStatusInAndExpiryDateBefore(
                List.of(UnitStatus.Available, UnitStatus.Reserved, UnitStatus.Quarantine), today);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Marking {} blood units as expired", expired.size());
        for (BloodUnit unit : expired) {
            unit.setStatus(UnitStatus.Expired);
            bloodUnitRepository.save(unit);
            liveEventPublisher.unitExpiring(Map.of(
                    "unitId", unit.getId(),
                    "bloodGroup", unit.getBloodGroup() != null ? unit.getBloodGroup().getValue() : "",
                    "expiryDate", unit.getExpiryDate(),
                    "status", "expired"
            ));
        }

        notificationService.notifyStaff(
                "Expired blood units",
                expired.size() + " blood unit(s) marked as expired",
                "inventory",
                "expiry-job"
        );
        liveEventPublisher.inventoryUpdated(Map.of("expiredCount", expired.size(), "action", "expiry_job"));
    }

    @Scheduled(cron = "${app.jobs.expiring-alert-cron:0 0 8 * * *}")
    @Transactional(readOnly = true)
    public void alertExpiringUnits() {
        LocalDate today = LocalDate.now();
        List<BloodUnit> expiring = bloodUnitRepository.findByExpiryDateBetweenAndStatus(
                today, today.plusDays(2), UnitStatus.Available);

        for (BloodUnit unit : expiring) {
            liveEventPublisher.unitExpiring(Map.of(
                    "unitId", unit.getId(),
                    "bloodGroup", unit.getBloodGroup() != null ? unit.getBloodGroup().getValue() : "",
                    "expiryDate", unit.getExpiryDate(),
                    "status", "expiring_soon"
            ));
        }

        if (!expiring.isEmpty()) {
            notificationService.notifyStaff(
                    "Units expiring soon",
                    expiring.size() + " blood unit(s) expiring within 48 hours",
                    "inventory",
                    "expiring-alert"
            );
        }
    }
}
