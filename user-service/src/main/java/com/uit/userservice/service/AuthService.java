package com.uit.userservice.service;

import com.uit.userservice.dto.request.*;
import com.uit.userservice.dto.response.FaceRegistrationResult;
import com.uit.userservice.dto.response.OtpResponse;
import com.uit.userservice.dto.response.TokenResponse;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.dto.response.ValidationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AuthService {

    // Old registration flow (keep for backward compatibility if needed)
    UserResponse register(RegisterRequest request);

    // New multi-step registration flow
    OtpResponse validateAndSendOtp(ValidateRegistrationRequest request);

    ValidationResponse verifyOtp(VerifyOtpRequest request);
    
    // Login with device switch OTP support
    com.uit.userservice.dto.auth.LoginResponse login(LoginRequest request);
    
    // Verify device switch OTP and complete login
    com.uit.userservice.dto.auth.LoginResponse verifyDeviceSwitchOtpAndLogin(
            com.uit.userservice.dto.request.VerifyDeviceSwitchOtpRequest verifyRequest,
            String username,
            String password,
            String deviceId
    );

    void logout(LogoutRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void changePassword(ChangePasswordRequest request, String userId, String username);

    // Forgot password flow
    com.uit.userservice.dto.response.ForgotPasswordOtpResponse forgotPasswordSendOtp(ForgotPasswordSendOtpRequest request);
    com.uit.userservice.dto.response.ForgotPasswordVerifyResponse forgotPasswordVerifyOtp(ForgotPasswordVerifyOtpRequest request);
    com.uit.userservice.dto.response.ForgotPasswordResetResponse forgotPasswordReset(ForgotPasswordResetRequest request);

    // Face registration (public - no auth required, for post-registration flow)
    FaceRegistrationResult registerFacePublic(String userId, List<MultipartFile> files);
    
    // Helper method to find user by username/email/phone
    com.uit.userservice.entity.User findByUsernameOrEmailOrPhone(String identifier);
}

