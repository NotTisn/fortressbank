package com.uit.userservice.dto.smartotp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for checking user's Smart OTP capabilities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartOtpStatusResponse {
    
    /**
     * Whether user has registered their face
     */
    private boolean faceRegistered;
    
    /**
     * Whether user has at least one active device
     */
    private boolean deviceRegistered;
    
    /**
     * Number of active devices
     */
    private int activeDeviceCount;
    
    /**
     * Status message
     */
    private String message;
}
