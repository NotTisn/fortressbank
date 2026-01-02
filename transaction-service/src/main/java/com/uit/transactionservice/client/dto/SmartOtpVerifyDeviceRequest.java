package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to verify Smart OTP (device signature) with user-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartOtpVerifyDeviceRequest {
    
    /**
     * Challenge ID from the challenge generation response.
     */
    private String challengeId;
    
    /**
     * Device ID that signed the challenge.
     */
    private String deviceId;
    
    /**
     * Base64-encoded signature of the challenge data.
     */
    private String signatureBase64;
}
