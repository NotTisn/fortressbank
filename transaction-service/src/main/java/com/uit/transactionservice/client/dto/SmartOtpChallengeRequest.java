package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to generate a Smart OTP challenge from user-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartOtpChallengeRequest {
    
    /**
     * User ID requesting the challenge.
     */
    private String userId;
    
    /**
     * Transaction ID for audit trail.
     */
    private String transactionId;
    
    /**
     * Challenge type: DEVICE_BIO or FACE_VERIFY.
     */
    private String challengeType;
}
