package com.bloodbank.bloodbank.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private String role;
    private String staffRole;
    private Map<String, Boolean> permissions;
}
