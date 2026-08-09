package com.bloodbank.bloodbank.dto.request;

import lombok.Data;

@Data
public class PayCompensationRequest {
    /** UI label or enum name, e.g. "Mobile Money" or "Mobile_Money" */
    private String paymentMethod;
    private String phoneNumber;
    private String notes;
}
