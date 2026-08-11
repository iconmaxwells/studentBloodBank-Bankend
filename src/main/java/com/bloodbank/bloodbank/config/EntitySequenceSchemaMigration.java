package com.bloodbank.bloodbank.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps the PostgreSQL check constraint on {@code entity_sequences.entity_type}
 * aligned with {@link com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType}.
 * Hibernate ddl-auto does not update existing check constraints when enum values are added.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class EntitySequenceSchemaMigration implements ApplicationRunner {

    private static final String CONSTRAINT_NAME = "entity_sequences_entity_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE entity_sequences DROP CONSTRAINT IF EXISTS " + CONSTRAINT_NAME);
            jdbcTemplate.execute("""
                    ALTER TABLE entity_sequences ADD CONSTRAINT entity_sequences_entity_type_check
                    CHECK (entity_type IN (
                        'DONOR', 'HOSPITAL', 'REQUEST', 'COLLECTION', 'BLOOD_UNIT',
                        'APPOINTMENT', 'TRANSACTION', 'SUPPLY_REQUEST', 'SERVICE_CHARGE'
                    ))
                    """);
            log.debug("Ensured {} includes all EntityType values", CONSTRAINT_NAME);
        } catch (Exception ex) {
            log.warn("Could not update {}: {}", CONSTRAINT_NAME, ex.getMessage());
        }
    }
}
