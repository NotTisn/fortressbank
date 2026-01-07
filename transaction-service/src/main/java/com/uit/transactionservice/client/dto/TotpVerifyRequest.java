package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for TOTP verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TotpVerifyRequest {
    private String userId;
    private String code;
    private String transactionId;
}
