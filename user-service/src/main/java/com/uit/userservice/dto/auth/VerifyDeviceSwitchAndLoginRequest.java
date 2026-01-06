package com.uit.userservice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to verify device switch OTP and complete login
 * Combines OTP verification with login credentials
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDeviceSwitchAndLoginRequest {
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    private String otp;
    
    private String deviceId;
}
