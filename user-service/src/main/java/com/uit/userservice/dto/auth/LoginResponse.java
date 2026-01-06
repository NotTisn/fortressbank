package com.uit.userservice.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private boolean requiresDeviceSwitchOtp;
    private String accessToken;
    private String refreshToken;
    private String message;
    
    public static LoginResponse success(String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .success(true)
                .requiresDeviceSwitchOtp(false)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
    
    public static LoginResponse requireOtp(String message) {
        return LoginResponse.builder()
                .success(false)
                .requiresDeviceSwitchOtp(true)
                .message(message)
                .build();
    }
}
