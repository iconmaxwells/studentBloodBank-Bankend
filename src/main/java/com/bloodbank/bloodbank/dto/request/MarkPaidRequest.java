package com.bloodbank.bloodbank.dto.request;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.CompensationMethod;
import lombok.Data;

@Data
public class MarkPaidRequest {
    private CompensationMethod method;
}
