package com.uit.userservice.service;

import com.uit.userservice.dto.request.DeviceSwitchOtpRequest;
import com.uit.userservice.dto.request.VerifyDeviceSwitchOtpRequest;
import com.uit.userservice.dto.response.DeviceSwitchOtpResponse;

public interface DeviceSwitchService {
    
    /**
     * Send OTP to user's phone for device switching
     * @param request Contains userId and newDeviceId
     * @return Response indicating success/failure
     */
    DeviceSwitchOtpResponse sendDeviceSwitchOtp(DeviceSwitchOtpRequest request);
    
    /**
     * Verify OTP for device switching
     * @param request Contains userId and otpCode
     * @return Response indicating verification success/failure
     */
    DeviceSwitchOtpResponse verifyDeviceSwitchOtp(VerifyDeviceSwitchOtpRequest request);
}
