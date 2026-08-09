package com.bloodbank.bloodbank.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReserveUnitRequest {
    @NotNull
    private UUID requestId;
}
