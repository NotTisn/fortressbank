package com.uit.userservice.dto.request;

import com.uit.userservice.validator.DobConstraint; // Import Validator tùy chỉnh
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "USERNAME_REQUIRED")
    @Size(min = 6, max = 20, message = "USERNAME_INVALID_LENGTH")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "USERNAME_NO_SPECIAL_CHARS")
    private String username;

    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "EMAIL_INVALID_FORMAT")
    private String email;

    @NotBlank(message = "PASSWORD_REQUIRED")
    @Size(min = 8, message = "PASSWORD_MIN_LENGTH")
    // Regex: Ít nhất 1 chữ hoa, 1 chữ thường, 1 số, 1 ký tự đặc biệt
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
            message = "PASSWORD_WEAK")
    private String password;

    @NotBlank(message = "FULLNAME_REQUIRED")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "FULLNAME_INVALID_FORMAT")
    private String fullName;

    // --- Validate Nghiệp vụ Ngân hàng ---

    @NotNull(message = "DOB_REQUIRED")
    @DobConstraint(minAge = 18, message = "USER_MUST_BE_18_YEARS_OLD") // Phải đủ 18 tuổi
    private LocalDate dob;

    @NotBlank(message = "CITIZEN_ID_REQUIRED")
    @Pattern(regexp = "^\\d{9}|\\d{12}$", message = "CITIZEN_ID_INVALID") // 9 hoặc 12 số
    private String citizenId;

    @Pattern(regexp = "^\\+84[0-9]{9,10}$", message = "PHONE_NUMBER_INVALID_FORMAT")
    private String phoneNumber;

    @NotBlank(message = "ACCOUNT_NUMBER_TYPE_REQUIRED")
    @Pattern(regexp = "^(PHONE_NUMBER|AUTO_GENERATE)$", message = "ACCOUNT_NUMBER_TYPE_INVALID")
    private String accountNumberType;

    @NotBlank(message = "PIN_REQUIRED")
    @Pattern(regexp = "^\\d{6}$", message = "PIN_MUST_BE_6_DIGITS")
    private String pin;
}