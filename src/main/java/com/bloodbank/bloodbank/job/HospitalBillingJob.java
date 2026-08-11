package com.bloodbank.bloodbank.job;

import com.bloodbank.bloodbank.service.HospitalBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalBillingJob {

    private final HospitalBillingService hospitalBillingService;

    /** Runs at 6:00 AM on the 1st day of each month. */
    @Scheduled(cron = "${app.jobs.hospital-billing-cron:0 0 6 1 * *}")
    public void issueMonthlyServiceCharges() {
        YearMonth period = YearMonth.now();
        log.info("Running scheduled hospital service charge job for {}", period);
        hospitalBillingService.ensureMonthlyChargesForPeriod(period);
    }

    /** Ensures current-month charges exist after startup (useful for demo environments). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureCurrentMonthChargesOnStartup() {
        hospitalBillingService.ensureMonthlyChargesForPeriod(YearMonth.now());
    }
}
