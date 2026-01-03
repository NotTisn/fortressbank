package com.uit.accountservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for admin to create account for a user
 */
public record AdminCreateAccountRequest(
        @NotBlank(message = "USER_ID_REQUIRED")
        String userId,

        @NotBlank(message = "ACCOUNT_NUMBER_TYPE_REQUIRED")
        @Pattern(regexp = "^(PHONE_NUMBER|AUTO_GENERATE)$", message = "ACCOUNT_NUMBER_TYPE_INVALID")
        String accountNumberType,

        // Optional: Phone number for PHONE_NUMBER type
        @Pattern(regexp = "^\\+84[0-9]{9,10}$", message = "PHONE_NUMBER_INVALID_FORMAT")
        String phoneNumber,

        // Optional: PIN to set for the account (6 digits)
        @Pattern(regexp = "^\\d{6}$", message = "PIN_MUST_BE_6_DIGITS")
        String pin
) { }
