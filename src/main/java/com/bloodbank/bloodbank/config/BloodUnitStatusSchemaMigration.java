package com.bloodbank.bloodbank.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures {@code blood_units.status} accepts the Quarantine value used when blood
 * is collected but not yet cleared by lab testing.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class BloodUnitStatusSchemaMigration implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "blood_units_status_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE blood_units DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
            jdbcTemplate.execute("""
                    ALTER TABLE blood_units ADD CONSTRAINT blood_units_status_check
                    CHECK (status IN ('Quarantine', 'Available', 'Reserved', 'Issued', 'Expired', 'Discarded'))
                    """);
            log.debug("Ensured {} includes Quarantine status", CONSTRAINT_NAME);
        } catch (Exception ex) {
            log.warn("Could not update {}: {}", CONSTRAINT_NAME, ex.getMessage());
        }
    }
}
