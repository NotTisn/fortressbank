package com.uit.userservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.dto.totp.*;
import com.uit.userservice.service.TotpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for TOTP (Smart OTP) management.
 * 
 * Endpoints:
 * - POST /totp/enroll - Start TOTP enrollment
 * - POST /totp/confirm - Confirm enrollment with first code
 * - POST /totp/verify - Verify TOTP code (for transactions)
 * - GET  /totp/status - Get current TOTP status
 * - POST /totp/disable - Disable TOTP
 * - POST /totp/recovery - Use recovery code
 * 
 * Internal endpoint (for other services):
 * - POST /totp/internal/verify - Verify TOTP code (service-to-service)
 */
@RestController
@RequestMapping("/totp")
@RequiredArgsConstructor
@Slf4j
public class TotpController {

    private final TotpService totpService;

    /**
     * Start TOTP enrollment.
     * Returns QR code and recovery codes.
     * User must scan QR and call /confirm with a valid code.
     */
    @PostMapping("/enroll")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<TotpEnrollmentResponse>> enrollTotp(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        
        log.info("TOTP enrollment request for user: {}", userId);
        
        TotpEnrollmentResponse response = totpService.enrollTotp(userId, email);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Confirm TOTP enrollment by providing first valid code.
     * Activates TOTP for the user.
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<TotpVerifyResponse>> confirmEnrollment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TotpConfirmRequest request) {
        
        String userId = jwt.getSubject();
        
        log.info("TOTP confirmation for user: {}", userId);
        
        TotpVerifyResponse response = totpService.confirmEnrollment(userId, request.getCode());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Verify a TOTP code.
     * Used by users to verify their codes manually.
     */
    @PostMapping("/verify")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<TotpVerifyResponse>> verifyCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TotpVerifyRequest request) {
        
        String userId = jwt.getSubject();
        
        TotpVerifyResponse response = totpService.verifyCode(
                userId, 
                request.getCode(), 
                request.getTransactionId());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get TOTP status for the current user.
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<TotpStatusResponse>> getStatus(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userId = jwt.getSubject();
        
        TotpStatusResponse response = totpService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Disable TOTP for the current user.
     * Requires current TOTP code for security.
     */
    @PostMapping("/disable")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<String>> disableTotp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TotpVerifyRequest request) {
        
        String userId = jwt.getSubject();
        
        // First verify the code
        TotpVerifyResponse verification = totpService.verifyCode(userId, request.getCode(), null);
        if (!verification.isValid()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Invalid TOTP code. Cannot disable.", null));
        }
        
        totpService.disableTotp(userId);
        
        log.info("TOTP disabled for user: {}", userId);
        
        return ResponseEntity.ok(ApiResponse.success("TOTP has been disabled."));
    }

    /**
     * Use a recovery code to disable TOTP.
     * For users who lost their authenticator device.
     */
    @PostMapping("/recovery")
    @PreAuthorize("hasRole('user')")
    public ResponseEntity<ApiResponse<String>> useRecoveryCode(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RecoveryCodeRequest request) {
        
        String userId = jwt.getSubject();
        
        boolean success = totpService.useRecoveryCode(userId, request.getRecoveryCode());
        
        if (success) {
            log.info("Recovery code used successfully for user: {}", userId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Recovery code accepted. TOTP has been disabled. Please re-enroll."));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "Invalid recovery code.", null));
        }
    }

    // ==================== Internal Endpoints (Service-to-Service) ====================

    /**
     * Internal endpoint for verifying TOTP codes.
     * Called by transaction-service for high-risk transaction verification.
     * 
     * SECURITY: This endpoint is permitAll() but protected by network policy.
     * Only callable from Docker internal network.
     */
    @PostMapping("/internal/verify")
    public ResponseEntity<ApiResponse<TotpVerifyResponse>> internalVerifyCode(
            @RequestBody InternalTotpVerifyRequest request) {
        
        log.info("Internal TOTP verification for user: {} transaction: {}", 
                request.getUserId(), request.getTransactionId());
        
        TotpVerifyResponse response = totpService.verifyCode(
                request.getUserId(),
                request.getCode(),
                request.getTransactionId());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Internal endpoint to check if user has TOTP enrolled.
     * Called by transaction-service to determine challenge type.
     */
    @GetMapping("/internal/status/{userId}")
    public ResponseEntity<ApiResponse<TotpStatusResponse>> internalGetStatus(
            @PathVariable("userId") String userId) {
        
        TotpStatusResponse response = totpService.getStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== Request DTOs for specific endpoints ====================

    @lombok.Data
    public static class RecoveryCodeRequest {
        private String recoveryCode;
    }

    @lombok.Data
    public static class InternalTotpVerifyRequest {
        private String userId;
        private String code;
        private String transactionId;
    }
}
