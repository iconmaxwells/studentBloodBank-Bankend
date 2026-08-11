package com.bloodbank.bloodbank.dto.request;

import com.bloodbank.bloodbank.entity.Donor;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.Gender;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.IdType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DonorCreateRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private String phone;

    @NotNull
    private LocalDate dateOfBirth;

    private Gender gender;
    private IdType idType;
    private String idNumber;
    private String address;
    private String city;
    private String region;
    private String postalCode;
    private Double weight;
    private Double height;

    public Donor toDonor() {
        return Donor.builder()
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .gender(gender)
                .idType(idType)
                .idNumber(idNumber)
                .address(address)
                .city(city)
                .region(region)
                .postalCode(postalCode)
                .weight(weight)
                .height(height)
                .build();
    }
}
