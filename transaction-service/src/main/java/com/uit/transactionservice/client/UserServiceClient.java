package com.uit.transactionservice.client;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.transactionservice.client.dto.SmartOtpStatusResponse;
import com.uit.transactionservice.client.dto.UserResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Client for synchronous communication with User Service
 * Handles fetching user information including phone numbers for OTP delivery
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final UserServiceFeignClient userServiceFeignClient;

    /**
     * Get user information by userId
     * @param userId The user ID
     * @return UserResponse containing user details including phoneNumber
     * @throws AppException if user not found or service unavailable
     */
    public UserResponse getUserById(String userId) {
        log.info("Fetching user information for userId: {}", userId);

        try {
            ApiResponse<UserResponse> response = userServiceFeignClient.getUserById(userId);

            if (response != null && response.getData() != null) {
                UserResponse userResponse = response.getData();
                log.info("Successfully fetched user information - UserId: {}, PhoneNumber: {}",
                        userId, maskPhoneNumber(userResponse.phoneNumber()));
                return userResponse;
            }

            log.error("User not found or empty response for userId: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId);

        } catch (FeignException.NotFound e) {
            log.error("User not found: {}", userId);
            throw new AppException(ErrorCode.USER_NOT_FOUND, "User not found: " + userId);

        } catch (FeignException.ServiceUnavailable e) {
            log.error("User service is unavailable: {}", e.getMessage());
            throw new AppException(ErrorCode.SERVICE_UNAVAILABLE, "User service is temporarily unavailable");

        } catch (FeignException e) {
            log.error("Feign error while fetching user {}: {} - {}", userId, e.status(), e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch user information: " + e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error while fetching user information", e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Get phone number for a specific user
     * @param userId The user ID
     * @return Phone number string
     * @throws AppException if user not found, phone number is null, or service unavailable
     */
    public String getPhoneNumberByUserId(String userId) {
        UserResponse user = getUserById(userId);

        if (user.phoneNumber() == null || user.phoneNumber().isEmpty()) {
            log.error("Phone number not found for user: {}", userId);
            throw new AppException(ErrorCode.PHONE_NUMBER_NOT_FOUND,
                    "Phone number not registered for user: " + userId);
        }

        return user.phoneNumber();
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

    // ========================================================================
    // BIOMETRIC / SMART OTP METHODS (Vietnamese e-banking style)
    // ========================================================================

    /**
     * Verify device signature for a transaction.
     * Used for DEVICE_BIO challenge verification.
     * 
     * @param challengeId The challenge ID
     * @param deviceId Device identifier
     * @param signatureBase64 The signature from the device
     * @return SmartOtpVerifyResponse with validation result
     */
    public com.uit.transactionservice.client.dto.SmartOtpVerifyResponse verifyDeviceSignature(
            String challengeId, String deviceId, String signatureBase64) {
        log.info("Verifying device signature for challenge: {} device: {}", challengeId, deviceId);
        // TODO: Implement actual call to user-service /devices/verify-signature
        // For now, accept all signatures (MVP behavior)
        log.warn("Device signature verification not implemented - accepting by default");
        return com.uit.transactionservice.client.dto.SmartOtpVerifyResponse.builder()
                .valid(true)
                .message("Verification not implemented - accepted by default")
                .build();
    }

    /**
     * Get Smart OTP status for a user.
     * Used to check if user has face/device registered for biometric auth.
     * 
     * @param userId The user ID
     * @return SmartOtpStatusResponse with hasFace and hasDevice flags
     */
    public SmartOtpStatusResponse getSmartOtpStatus(String userId) {
        log.info("Getting Smart OTP status for user: {}", userId);
        // TODO: Implement actual call to user-service /smart-otp/status/{userId}
        // For now, return defaults (no biometric registered)
        log.warn("Smart OTP status not implemented - returning defaults");
        return SmartOtpStatusResponse.builder()
                .hasDevice(false)
                .hasFace(false)
                .deviceCount(0)
                .build();
    }

    /**
     * Generate a Smart OTP challenge for biometric authentication.
     * 
     * @param userId The user ID
     * @param correlationId Transaction correlation ID
     * @param challengeType Type of challenge (DEVICE_BIO or FACE_VERIFY)
     * @return SmartOtpChallengeResponse with challengeId and challengeData
     */
    public com.uit.transactionservice.client.dto.SmartOtpChallengeResponse generateChallenge(
            String userId, String correlationId, String challengeType) {
        log.info("Generating {} challenge for user: {}", challengeType, userId);
        // TODO: Implement actual call to user-service /smart-otp/challenge/generate
        // For now, return mock challenge
        log.warn("Smart OTP challenge generation not implemented - returning mock");
        return com.uit.transactionservice.client.dto.SmartOtpChallengeResponse.builder()
                .challengeId(java.util.UUID.randomUUID().toString())
                .challengeData("MOCK_CHALLENGE_DATA_" + System.currentTimeMillis())
                .ttlSeconds(300)
                .build();
    }
}
