package com.bloodbank.bloodbank.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyActionRequest {
    @NotBlank
    private String password;
}
