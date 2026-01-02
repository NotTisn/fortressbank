package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from TOTP verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpVerifyResponse {
    private boolean valid;
    private String message;
    private int remainingAttempts;
}
