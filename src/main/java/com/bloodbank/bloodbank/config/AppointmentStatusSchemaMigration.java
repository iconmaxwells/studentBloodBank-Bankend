package com.bloodbank.bloodbank.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps the PostgreSQL check constraint on {@code appointments.status}
 * aligned with {@link com.bloodbank.bloodbank.entity.enums.DomainEnums.AppointmentStatus}.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class AppointmentStatusSchemaMigration implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "appointments_status_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE appointments DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
            jdbcTemplate.execute("""
                    ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
                    CHECK (status IN (
                        'Pending', 'Scheduled', 'Confirmed', 'Checked_In', 'In_Screening',
                        'Completed', 'Cancelled', 'No_Show'
                    ))
                    """);
            log.debug("Ensured {} includes all AppointmentStatus values", CONSTRAINT_NAME);
        } catch (Exception ex) {
            log.warn("Could not update {}: {}", CONSTRAINT_NAME, ex.getMessage());
        }
    }
}
