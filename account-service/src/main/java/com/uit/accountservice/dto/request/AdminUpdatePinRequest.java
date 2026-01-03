package com.uit.accountservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for admin to update account PIN
 */
public record AdminUpdatePinRequest(
        @NotBlank(message = "NEW_PIN_REQUIRED")
        @Pattern(regexp = "^\\d{6}$", message = "PIN_MUST_BE_6_DIGITS")
        String newPin
) { }
