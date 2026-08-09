package com.bloodbank.bloodbank.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateTestRequest {
    @NotNull
    private UUID collectionId;
}
