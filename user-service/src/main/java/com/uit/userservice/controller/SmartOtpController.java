package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.smartotp.*;
import com.uit.userservice.service.SmartOtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Smart OTP Controller - Vietnamese e-banking style verification.
 * 
 * User-facing endpoints:
 * - GET  /smart-otp/status - Check what verification methods are available
 * - POST /smart-otp/verify-device - Submit device signature response
 * - POST /smart-otp/verify-face - Submit face image for verification
 * 
 * Internal endpoints (for transaction-service):
 * - POST /smart-otp/internal/challenge - Generate a verification challenge
 * - POST /smart-otp/internal/verify-device - Verify device signature (service-to-service)
 * - GET  /smart-otp/internal/status/{userId} - Check user's capabilities
 */
@RestController
@RequestMapping("/smart-otp")
@RequiredArgsConstructor
@Slf4j
public class SmartOtpController {

    private final SmartOtpService smartOtpService;

    // ==================== User-Facing Endpoints ====================

    /**
     * Get current user's Smart OTP status.
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<SmartOtpStatusResponse>> getStatus(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        SmartOtpStatusResponse status = smartOtpService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * Verify with device signature.
     * Client signs the challenge with device private key (unlocked by biometric).
     */
    @PostMapping("/verify-device")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<SmartOtpVerifyResponse>> verifyDevice(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody DeviceVerifyRequest request) {
        
        String userId = jwt.getSubject();
        log.info("User {} submitting device verification for challenge {}", userId, request.getChallengeId());
        
        SmartOtpVerifyResponse response = smartOtpService.verifyDeviceSignature(
                request.getChallengeId(),
                request.getDeviceId(),
                request.getSignatureBase64());
        
        // Security: verify the challenge was for this user
        if (response.isValid() && !userId.equals(response.getUserId())) {
            log.error("User {} tried to verify challenge belonging to {}", userId, response.getUserId());
            return ResponseEntity.ok(ApiResponse.success(SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Unauthorized")
                    .build()));
        }
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Verify with face image.
     */
    @PostMapping(value = "/verify-face", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<SmartOtpVerifyResponse>> verifyFace(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("challengeId") String challengeId,
            @RequestPart("files") List<MultipartFile> files) {
        
        String userId = jwt.getSubject();
        log.info("User {} submitting face verification for challenge {}", userId, challengeId);
        
        SmartOtpVerifyResponse response = smartOtpService.verifyFace(challengeId, files);
        
        // Security: verify the challenge was for this user
        if (response.isValid() && !userId.equals(response.getUserId())) {
            log.error("User {} tried to verify challenge belonging to {}", userId, response.getUserId());
            return ResponseEntity.ok(ApiResponse.success(SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Unauthorized")
                    .build()));
        }
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== Internal Endpoints (Service-to-Service) ====================

    /**
     * Generate a verification challenge.
     * Called by transaction-service when a transaction requires Smart OTP.
     */
    @PostMapping("/internal/challenge")
    public ResponseEntity<ApiResponse<SmartOtpChallengeResponse>> generateChallenge(
            @RequestBody InternalChallengeRequest request) {
        
        log.info("Internal challenge request: user={} txn={} type={}", 
                request.getUserId(), request.getTransactionId(), request.getChallengeType());
        
        SmartOtpChallengeResponse response = smartOtpService.generateChallenge(
                request.getUserId(),
                request.getTransactionId(),
                request.getChallengeType());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Internal device signature verification.
     * Called by transaction-service to verify without user JWT.
     */
    @PostMapping("/internal/verify-device")
    public ResponseEntity<ApiResponse<SmartOtpVerifyResponse>> internalVerifyDevice(
            @RequestBody InternalDeviceVerifyRequest request) {
        
        log.info("Internal device verify: challenge={} device={}", 
                request.getChallengeId(), request.getDeviceId());
        
        SmartOtpVerifyResponse response = smartOtpService.verifyDeviceSignature(
                request.getChallengeId(),
                request.getDeviceId(),
                request.getSignatureBase64());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Internal status check.
     * Called by transaction-service to determine what verification to request.
     */
    @GetMapping("/internal/status/{userId}")
    public ResponseEntity<ApiResponse<SmartOtpStatusResponse>> internalGetStatus(
            @PathVariable("userId") String userId) {
        
        SmartOtpStatusResponse status = smartOtpService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    // ==================== Request DTOs ====================

    @lombok.Data
    public static class DeviceVerifyRequest {
        private String challengeId;
        private String deviceId;
        private String signatureBase64;
    }

    @lombok.Data
    public static class InternalChallengeRequest {
        private String userId;
        private String transactionId;
        private String challengeType; // DEVICE_BIO or FACE_VERIFY
    }

    @lombok.Data
    public static class InternalDeviceVerifyRequest {
        private String challengeId;
        private String deviceId;
        private String signatureBase64;
    }
}
