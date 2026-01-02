package com.uit.userservice.dto.totp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for TOTP verification result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpVerifyResponse {

    /**
     * Whether the TOTP code was valid
     */
    private boolean valid;

    /**
     * Message with result details
     */
    private String message;

    /**
     * User ID (for audit)
     */
    private String userId;

    /**
     * Transaction ID (if provided)
     */
    private String transactionId;

    /**
     * Remaining attempts before lockout (if failed)
     */
    private Integer remainingAttempts;

    /**
     * Whether the account is locked due to too many failed attempts
     */
    private boolean lockedOut;
}
