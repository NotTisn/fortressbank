package com.uit.userservice.dto.totp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for TOTP enrollment.
 * Contains all information needed for user to set up their authenticator app.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpEnrollmentResponse {

    /**
     * The secret key in Base32 format.
     * User can manually enter this in their authenticator app.
     */
    private String secretKey;

    /**
     * QR code as a data URI (data:image/png;base64,...).
     * User can scan this with their authenticator app.
     */
    private String qrCodeDataUri;

    /**
     * The provisioning URI (otpauth://totp/...).
     * Some apps prefer this format.
     */
    private String otpAuthUri;

    /**
     * Recovery codes for account recovery.
     * User should save these in a safe place.
     * Only shown once during enrollment!
     */
    private List<String> recoveryCodes;

    /**
     * Instructions for the user
     */
    private String instructions;

    /**
     * Status of the enrollment (PENDING until confirmed)
     */
    private String status;
}
