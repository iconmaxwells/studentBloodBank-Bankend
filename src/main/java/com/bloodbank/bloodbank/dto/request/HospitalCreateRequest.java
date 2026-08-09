package com.bloodbank.bloodbank.dto.request;

import com.bloodbank.bloodbank.entity.Hospital;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.HospitalCapacity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HospitalCreateRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    private String name;

    private String registrationNumber;
    private String location;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;
    private String emergencyPhone;
    private String website;
    private HospitalCapacity capacity;
    private Integer beds;
    private List<String> departments;
    private Map<String, Object> primaryContact;
    private Map<String, Object> bloodBankCoordinator;
    private String operatingHours;
    private String accreditation;
    private List<Map<String, Object>> licenses;

    public Hospital toHospital() {
        return Hospital.builder()
                .name(name)
                .registrationNumber(registrationNumber)
                .location(location)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .phone(phone)
                .emergencyPhone(emergencyPhone)
                .email(email)
                .website(website)
                .capacity(capacity)
                .beds(beds)
                .departments(departments)
                .primaryContact(primaryContact)
                .bloodBankCoordinator(bloodBankCoordinator)
                .operatingHours(operatingHours)
                .accreditation(accreditation)
                .licenses(licenses)
                .build();
    }
}
