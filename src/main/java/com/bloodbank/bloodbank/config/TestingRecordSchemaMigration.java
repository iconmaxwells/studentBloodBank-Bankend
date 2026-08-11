package com.bloodbank.bloodbank.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps the PostgreSQL check constraint on {@code testing_records.overall_status}
 * aligned with {@link com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus}.
 * Hibernate ddl-auto does not update existing check constraints when enum values are added.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class TestingRecordSchemaMigration implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "testing_records_overall_status_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE testing_records DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
            jdbcTemplate.execute("""
                    ALTER TABLE testing_records ADD CONSTRAINT testing_records_overall_status_check
                    CHECK (overall_status IN ('Pending', 'Completed', 'Passed', 'Failed'))
                    """);
            log.debug("Ensured {} includes all TestOverallStatus values", CONSTRAINT_NAME);
        } catch (Exception ex) {
            log.warn("Could not update {}: {}", CONSTRAINT_NAME, ex.getMessage());
        }
    }
}
