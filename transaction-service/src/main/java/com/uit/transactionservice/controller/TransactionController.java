package com.uit.transactionservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.sharedkernel.security.JwtUtils;
import com.uit.transactionservice.dto.VerifyOTPRequest;
import com.uit.transactionservice.dto.request.ConfirmFaceAuthRequest;
import com.uit.transactionservice.dto.request.CreateTransferRequest;
import com.uit.transactionservice.dto.request.ResendOtpRequest;
import com.uit.transactionservice.dto.response.TransactionLimitResponse;
import com.uit.transactionservice.dto.response.TransactionResponse;
import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.security.RequireRole;
import com.uit.transactionservice.service.TransactionLimitService;
import com.uit.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionLimitService transactionLimitService;
    private final com.uit.transactionservice.client.UserServiceClient userServiceClient;

    /**
     * Create a new transfer transaction (with OTP)
     * POST /transactions/transfers
     *
     * @param request The transfer request containing sender, receiver, and amount
     * @param jwt JWT token containing user identity and phoneNumber (automatically injected by Spring Security)
     * @return TransactionResponse with transaction details and PENDING_OTP status
     */
    @PostMapping("/transfers")
    // @RequireRole("user")
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        try {
            // Extract userId and phoneNumber from JWT token using utility
            String userId = JwtUtils.getUserId(jwt);
//             @SuppressWarnings("unchecked")
//             Map<String, Object> userInfo = (Map<String, Object>) httpRequest.getAttribute("userInfo");
//             String userId = "test-user"; // Default for testing
//             String phoneNumber = "+84857311444"; // Default for testing
            
            log.info("Creating transfer for user: {}", userId);
            
            // TROUBLESHOOTING: Log all JWT claims to see what's available
            var allClaims = JwtUtils.getAllClaims(jwt);
            log.info("=== JWT CLAIMS TROUBLESHOOTING ===");
            log.info("Available claims: {}", allClaims.keySet());
            log.info("phone_number claim (underscore): {}", allClaims.get("phone_number"));
            log.info("phoneNumber claim (camelCase): {}", allClaims.get("phoneNumber"));
            log.info("===================================");
            
            String phoneNumber = JwtUtils.getPhoneNumber(jwt);
            log.info("JwtUtils.getPhoneNumber() returned: {}", phoneNumber);

            // Fallback: If phoneNumber not in token, fetch from user-service (backward compatibility)
            if (phoneNumber == null || phoneNumber.isEmpty()) {
                log.warn("phoneNumber not found in JWT token for user {}. Falling back to user-service API.", userId);
                phoneNumber = userServiceClient.getPhoneNumberByUserId(userId);
            } else {
                log.info("✅ phoneNumber extracted from JWT token: {}", maskPhoneNumber(phoneNumber));
            }
            
            log.info("Phone number retrieved for user {}: {}", userId, maskPhoneNumber(phoneNumber));

            TransactionResponse response = transactionService.createTransfer(request, userId, phoneNumber);

            log.info("Transfer created successfully: {}", response.getTransactionId());
            return ResponseEntity.status(HttpStatus.CREATED)
                   .body(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("=== CREATE TRANSFER FAILED ===", e);
            log.error("Error type: {}", e.getClass().getName());
            log.error("Error message: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {}", e.getCause().getMessage());
            }
            throw e;
        }
    }

    /**
     * Verify OTP for transaction
     * POST /transactions/verify-otp
     */
    @PostMapping("/verify-otp")
    // @RequireRole("user")
    public ResponseEntity<ApiResponse<TransactionResponse>> verifyOTP(
            @Valid @RequestBody VerifyOTPRequest request) {

        TransactionResponse response = transactionService.verifyOTP(request.getTransactionId(), request.getOtpCode());
        
        // Check if transaction failed and return appropriate HTTP status
        if (response.getStatus() == TransactionStatus.FAILED || 
            response.getStatus() == TransactionStatus.OTP_EXPIRED) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, response.getFailureReason(), response));
        }
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Resend OTP for a transaction
     * POST /transactions/resend-otp
     */
    @PostMapping("/resend-otp")
    // @RequireRole("user")
    public ResponseEntity<ApiResponse<String>> resendOtp(
            @Valid @RequestBody ResendOtpRequest request) {

       String otp= transactionService.resendOtp(request.getTransactionId());
        return ResponseEntity.ok(ApiResponse.success(otp));
    }

    /**
     * Get transaction history with pagination and filtering
     * GET /transactions?offset=0&limit=20&status=COMPLETED
     */
    @GetMapping
    // @RequireRole("user")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactions(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) TransactionStatus status) {

        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());

        Page<TransactionResponse> transactions;
        if (status != null) {
            transactions = transactionService.getTransactionHistoryByStatus(status, pageable);
        } else {
            // If no filter, require status parameter (or return empty/all if intended for admin)
            throw new IllegalArgumentException("Status parameter is required for general search");
        }

        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * Get transaction history for a specific account number (Infinite Scroll)
     * GET /transactions/{accountNumber}/history?offset=0&limit=10&type=SENT
     */
    @GetMapping("/{accountNumber}/history")
    // @RequireRole("user")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getAccountTransactionHistory(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String type) {

        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("createdAt").descending());
        
        Page<TransactionResponse> transactions = transactionService.getTransactionHistory(accountNumber, type, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    /**
     * Get transaction by ID
     * GET /transactions/{txId}
     */
    @GetMapping("/{txId}")
    // @RequireRole("user")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionById(
            @PathVariable("txId") String txId) {

        UUID transactionId = UUID.fromString(txId);
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get transaction limits for an account
     * GET /transactions/limits?accountId=xxx
     */
    @GetMapping("/limits")
    @RequireRole("user")
    public ResponseEntity<ApiResponse<TransactionLimitResponse>> getTransactionLimits(
            @RequestParam String accountId) {

        TransactionLimitResponse response = transactionLimitService.getTransactionLimits(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Mask phone number for logging (show only last 4 digits)
     * Example: +84384929107 -> +84******9107
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() <= 4) {
            return "****";
        }
        int visibleDigits = 4;
        int maskLength = phoneNumber.length() - visibleDigits - 3; // -3 for country code
        String countryCode = phoneNumber.substring(0, 3);
        String lastDigits = phoneNumber.substring(phoneNumber.length() - visibleDigits);
        return countryCode + "*".repeat(Math.max(0, maskLength)) + lastDigits;
    }
    
    @PostMapping("/internal/face-auth-success")
    public ResponseEntity<ApiResponse<Void>> confirmFaceAuth(
            @RequestBody ConfirmFaceAuthRequest request) {
        
        transactionService.confirmFaceAuth(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
