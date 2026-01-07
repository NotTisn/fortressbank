package com.uit.userservice.dto.smartotp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Smart OTP verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartOtpVerifyResponse {
    
    /**
     * Whether verification was successful
     */
    private boolean valid;
    
    /**
     * User ID (for audit)
     */
    private String userId;
    
    /**
     * Transaction ID that was verified
     */
    private String transactionId;
    
    /**
     * Human-readable result message
     */
    private String message;
}
