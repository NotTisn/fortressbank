package com.uit.accountservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PinRequest(
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "^\\d{6}$", message = "PIN must be 6 digits")
        String newPin
) {}