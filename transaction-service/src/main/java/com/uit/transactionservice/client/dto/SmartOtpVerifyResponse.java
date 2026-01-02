package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Smart OTP verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartOtpVerifyResponse {
    
    /**
     * Whether the verification was successful.
     */
    private boolean valid;
    
    /**
     * Success or error message.
     */
    private String message;
    
    /**
     * Transaction ID (passed back for correlation).
     */
    private String transactionId;
}
