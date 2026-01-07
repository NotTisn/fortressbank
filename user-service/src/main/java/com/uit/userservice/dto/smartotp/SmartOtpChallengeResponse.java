package com.uit.userservice.dto.smartotp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response when generating a Smart OTP challenge.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartOtpChallengeResponse {
    
    /**
     * Unique challenge ID (used when submitting verification)
     */
    private String challengeId;
    
    /**
     * Random data to be signed by device (for DEVICE_BIO)
     * or just for tracking (for FACE_VERIFY)
     */
    private String challengeData;
    
    /**
     * DEVICE_BIO or FACE_VERIFY
     */
    private String challengeType;
    
    /**
     * Seconds until challenge expires
     */
    private int expiresInSeconds;
    
    /**
     * User-friendly message for the client to display
     */
    private String message;
}
