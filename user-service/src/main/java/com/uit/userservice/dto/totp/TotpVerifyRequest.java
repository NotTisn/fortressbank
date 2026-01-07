package com.uit.userservice.dto.totp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for verifying a TOTP code.
 * Used during high-risk transaction verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpVerifyRequest {

    /**
     * 6-digit TOTP code from authenticator app
     */
    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be exactly 6 digits")
    private String code;

    /**
     * Optional: Transaction ID for audit trail
     */
    private String transactionId;
}
