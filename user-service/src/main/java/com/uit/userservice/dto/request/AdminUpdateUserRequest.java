package com.uit.userservice.dto.request;

import com.uit.userservice.validator.DobConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {

    @NotBlank(message = "FULLNAME_REQUIRED")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "FULLNAME_INVALID_FORMAT")
    private String fullName;

    @Email(message = "EMAIL_INVALID_FORMAT")
    private String email;

    @DobConstraint(minAge = 18, message = "USER_MUST_BE_18_YEARS_OLD")
    private LocalDate dob;

    @Pattern(regexp = "^\\+84[0-9]{9,10}$", message = "PHONE_NUMBER_INVALID_FORMAT")
    private String phoneNumber;
}
