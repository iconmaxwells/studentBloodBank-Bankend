package com.bloodbank.bloodbank.service;

import com.bloodbank.bloodbank.entity.EntitySequence;
import com.bloodbank.bloodbank.entity.enums.BloodProductType;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EntityType;
import com.bloodbank.bloodbank.repository.EntitySequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisplayCodeService {

    private final EntitySequenceRepository entitySequenceRepository;

    @Transactional
    public String nextCode(EntityType entityType) {
        EntitySequence sequence = entitySequenceRepository.findForUpdate(entityType)
                .orElseGet(() -> entitySequenceRepository.save(EntitySequence.builder()
                        .entityType(entityType)
                        .lastValue(0L)
                        .build()));
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        entitySequenceRepository.save(sequence);
        return formatCode(entityType, next);
    }

    private String formatCode(EntityType entityType, long value) {
        return switch (entityType) {
            case DONOR -> "D" + String.format("%03d", value);
            case HOSPITAL -> "H" + String.format("%03d", value);
            case REQUEST -> "REQ" + String.format("%03d", value);
            case COLLECTION -> "COL" + String.format("%03d", value);
            case APPOINTMENT -> "APT" + String.format("%03d", value);
            case TRANSACTION -> "TXN" + String.format("%03d", value);
            case BLOOD_UNIT -> "UNIT" + String.format("%03d", value);
            case SUPPLY_REQUEST -> "SR" + String.format("%03d", value);
            case SERVICE_CHARGE -> "INV" + String.format("%03d", value);
        };
    }

    public String nextBloodUnitCode(BloodProductType productType) {
        EntityType key = EntityType.BLOOD_UNIT;
        EntitySequence sequence = entitySequenceRepository.findForUpdate(key)
                .orElseGet(() -> entitySequenceRepository.save(EntitySequence.builder()
                        .entityType(key)
                        .lastValue(0L)
                        .build()));
        long next = sequence.getLastValue() + 1;
        sequence.setLastValue(next);
        entitySequenceRepository.save(sequence);
        return productType.getValue() + "-" + String.format("%03d", next);
    }
}
