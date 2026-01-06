package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.request.DeviceSwitchOtpRequest;
import com.uit.userservice.dto.request.VerifyDeviceSwitchOtpRequest;
import com.uit.userservice.dto.response.DeviceSwitchOtpResponse;
import com.uit.userservice.service.DeviceSwitchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Device Switch Controller
 * Handles OTP-based device switching for single-device authentication
 * Called by Keycloak authenticator when device conflict is detected
 */
@RestController
@RequestMapping("/api/users/device-switch")
@RequiredArgsConstructor
@Slf4j
public class DeviceSwitchController {

    private final DeviceSwitchService deviceSwitchService;

    /**
     * Send OTP to user's phone for device switching
     * Called by SingleDeviceAuthenticator when Device B tries to login while Device A is active
     * 
     * @param request Contains userId and newDeviceId
     * @return Response indicating OTP was sent
     */
    @PostMapping("/send-otp")
    public ApiResponse<DeviceSwitchOtpResponse> sendDeviceSwitchOtp(
            @Valid @RequestBody DeviceSwitchOtpRequest request) {
        
        log.info("Received device switch OTP request for user: {}", request.getUserId());
        
        DeviceSwitchOtpResponse response = deviceSwitchService.sendDeviceSwitchOtp(request);
        
        if (response.isSuccess()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.error(400, response.getMessage(), response);
        }
    }

    /**
     * Verify OTP for device switching
     * Called by DeviceSwitchOtpAuthenticator when user submits OTP
     * 
     * @param request Contains userId and otpCode
     * @return Response indicating verification result
     */
    @PostMapping("/verify-otp")
    public ApiResponse<DeviceSwitchOtpResponse> verifyDeviceSwitchOtp(
            @Valid @RequestBody VerifyDeviceSwitchOtpRequest request) {
        
        log.info("Received device switch OTP verification for user: {}", request.getUserId());
        
        DeviceSwitchOtpResponse response = deviceSwitchService.verifyDeviceSwitchOtp(request);
        
        if (response.isSuccess()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.error(400, response.getMessage(), response);
        }
    }
}
