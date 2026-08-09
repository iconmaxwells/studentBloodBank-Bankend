package com.bloodbank.bloodbank.dto.request;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.TestOverallStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompleteTestRequest {
    @NotNull
    private TestOverallStatus overallStatus;
}
