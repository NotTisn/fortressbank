package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from Smart OTP challenge generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartOtpChallengeResponse {
    
    /**
     * Unique challenge ID for verification.
     */
    private String challengeId;
    
    /**
     * Challenge type: DEVICE_BIO or FACE_VERIFY.
     */
    private String challengeType;
    
    /**
     * Base64-encoded random bytes for device to sign (for DEVICE_BIO).
     */
    private String challengeData;
    
    /**
     * Time-to-live in seconds.
     */
    private int ttlSeconds;
}
