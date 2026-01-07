package com.uit.userservice.dto.totp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for TOTP status check.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpStatusResponse {

    /**
     * Whether TOTP is enrolled and active for this user
     */
    private boolean enrolled;

    /**
     * Status of TOTP: NONE, PENDING, ACTIVE, DISABLED
     */
    private String status;

    /**
     * When TOTP was activated (if applicable)
     */
    private String activatedAt;

    /**
     * Whether account is locked due to failed attempts
     */
    private boolean lockedOut;

    /**
     * Message with instructions or status
     */
    private String message;
}
