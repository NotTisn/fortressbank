package com.uit.accountservice.dto.request;

import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for updating account information (Admin only).
 * Currently supports updating account status.
 * Can be extended with additional fields in the future.
 */
public record UpdateAccountRequest(
        // Optional: Update account status
        @Pattern(regexp = "^(ACTIVE|LOCKED|CLOSED)$", message = "ACCOUNT_STATUS_INVALID")
        String status
) {
}
