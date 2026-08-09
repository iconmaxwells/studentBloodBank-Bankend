package com.bloodbank.bloodbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DiscardUnitRequest {
    @NotBlank
    private String reason;
}
