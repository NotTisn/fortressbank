package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from checking Smart OTP status for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartOtpStatusResponse {
    
    /**
     * Whether the user has at least one registered device (for DEVICE_BIO challenges).
     */
    private boolean hasDevice;
    
    /**
     * Whether the user has registered face biometric (for FACE_VERIFY challenges).
     */
    private boolean hasFace;
    
    /**
     * Number of active registered devices.
     */
    private int deviceCount;
}
