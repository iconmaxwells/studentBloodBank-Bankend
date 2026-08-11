package com.bloodbank.bloodbank.dto.request;

import com.bloodbank.bloodbank.entity.enums.BloodGroup;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.EligibilityResult;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CompleteScreeningRequest {
    @NotNull
    private EligibilityResult eligibilityResult;

    private String deferralReason;
    private LocalDate deferralUntil;
    private String notes;
    private BloodGroup bloodGroup;
    private UUID appointmentId;
}
