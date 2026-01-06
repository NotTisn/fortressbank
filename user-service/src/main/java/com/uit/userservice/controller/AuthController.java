package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.request.*;
import com.uit.userservice.dto.response.FaceRegistrationResult;
import com.uit.userservice.dto.response.OtpResponse;
import com.uit.userservice.dto.response.TokenResponse;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.dto.response.ValidationResponse;
import com.uit.userservice.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * Step 1: Validate registration data (email, phoneNumber, citizenId) and send OTP
     */
    @PostMapping("/validate-and-send-otp")
    public ApiResponse<OtpResponse> validateAndSendOtp(@Valid @RequestBody ValidateRegistrationRequest request) {
        return ApiResponse.success(authService.validateAndSendOtp(request));
    }

    /**
     * Step 2: Verify OTP
     */
    @PostMapping("/verify-otp")
    public ApiResponse<ValidationResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.success(authService.verifyOtp(request));
    }


    // Register
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    // ==================== OTHER AUTH ENDPOINTS ====================
    
    /**
     * Login with automatic device conflict detection
     * Returns requiresDeviceSwitchOtp=true if another device is logged in
     */
    @PostMapping("/login")
    public ApiResponse<com.uit.userservice.dto.auth.LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
    
    /**
     * Verify device switch OTP and complete login
     * Used when login returns requiresDeviceSwitchOtp=true
     */
    @PostMapping("/verify-device-switch-otp")
    public ApiResponse<com.uit.userservice.dto.auth.LoginResponse> verifyDeviceSwitchOtp(
            @Valid @RequestBody com.uit.userservice.dto.auth.VerifyDeviceSwitchAndLoginRequest request) {
        
        // Find user to get keycloakId
        com.uit.userservice.entity.User user = authService.findByUsernameOrEmailOrPhone(request.getUsername());
        
        // Build verify request
        com.uit.userservice.dto.request.VerifyDeviceSwitchOtpRequest verifyRequest = 
                new com.uit.userservice.dto.request.VerifyDeviceSwitchOtpRequest(
                        user.getId(),
                        request.getOtp()
                );
        
        return ApiResponse.success(
                authService.verifyDeviceSwitchOtpAndLogin(
                        verifyRequest, 
                        request.getUsername(), 
                        request.getPassword(), 
                        request.getDeviceId()
                )
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    // ==================== FORGOT PASSWORD FLOW ====================

    /**
     * Step 1: Send OTP to phone number for password reset
     */
    @PostMapping("/forgot-password/send-otp")
    public ApiResponse<com.uit.userservice.dto.response.ForgotPasswordOtpResponse> forgotPasswordSendOtp(
            @Valid @RequestBody ForgotPasswordSendOtpRequest request) {
        return ApiResponse.success(authService.forgotPasswordSendOtp(request));
    }

    /**
     * Step 2: Verify OTP and get verification token
     */
    @PostMapping("/forgot-password/verify-otp")
    public ApiResponse<com.uit.userservice.dto.response.ForgotPasswordVerifyResponse> forgotPasswordVerifyOtp(
            @Valid @RequestBody ForgotPasswordVerifyOtpRequest request) {
        return ApiResponse.success(authService.forgotPasswordVerifyOtp(request));
    }

    /**
     * Step 3: Reset password with verification token
     */
    @PostMapping("/forgot-password/reset")
    public ApiResponse<com.uit.userservice.dto.response.ForgotPasswordResetResponse> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        return ApiResponse.success(authService.forgotPasswordReset(request));
    }

    @PostMapping(value = "/register-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FaceRegistrationResult> registerFace(
            @RequestPart("user_id") @NotBlank String userId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        var result = authService.registerFacePublic(userId, files);

        if (!result.isSuccess()) {
            return ApiResponse.error(400, result.getMessage(), null);
        }

        return ApiResponse.success(result);
    }
}
