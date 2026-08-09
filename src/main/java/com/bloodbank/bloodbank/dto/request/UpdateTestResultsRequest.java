package com.bloodbank.bloodbank.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateTestResultsRequest {
    @NotEmpty
    private List<Map<String, Object>> tests;
}
