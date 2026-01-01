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
    TokenResponse login(LoginRequest request);

    void logout(LogoutRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void changePassword(ChangePasswordRequest request, String userId, String username);

    // Face registration (public - no auth required, for post-registration flow)
    FaceRegistrationResult registerFacePublic(String userId, List<MultipartFile> files);
}

