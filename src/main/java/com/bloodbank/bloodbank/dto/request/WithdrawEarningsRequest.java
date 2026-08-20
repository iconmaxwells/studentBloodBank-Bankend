package com.bloodbank.bloodbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WithdrawEarningsRequest {
    /** Mobile_Money, Bank_Transfer, or Cash */
    private String paymentMethod;

    @NotBlank
    private String phoneNumber;

    private String notes;
}
