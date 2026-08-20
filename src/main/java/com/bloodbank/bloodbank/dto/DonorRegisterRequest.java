package com.bloodbank.bloodbank.dto;

import com.bloodbank.bloodbank.entity.enums.DomainEnums.Gender;
import com.bloodbank.bloodbank.entity.enums.DomainEnums.IdType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DonorRegisterRequest {
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8)
    private String password;
    @NotNull
    private LocalDate dateOfBirth;
    @NotNull
    private Gender gender;
    @NotNull
    private IdType idType;
    @NotBlank
    private String idNumber;
    @AssertTrue
    private Boolean agreeToTerms;

    /** When true, donor opts out of monetary compensation (voluntary donation). */
    private Boolean isVoluntary;
}
