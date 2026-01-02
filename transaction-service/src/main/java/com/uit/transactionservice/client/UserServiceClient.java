package com.uit.transactionservice.client;

import com.uit.transactionservice.client.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Client for calling user-service Smart OTP verification.
 * Used for biometric verification on medium/high-risk transactions.
 * 
 * Vietnamese e-banking style:
 * - MEDIUM risk → DEVICE_BIO (fingerprint/PIN unlocks device key to sign challenge)
 * - HIGH risk → FACE_VERIFY (face re-verification via AI service)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.user-service.url:http://localhost:4000}")
    private String userServiceUrl;

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    // ========================================================================
    // SMART OTP METHODS (New Vietnamese-style biometric verification)
    // ========================================================================

    /**
     * Generate a Smart OTP challenge for the user.
     * 
     * @param userId User ID
     * @param transactionId Transaction ID (for audit)
     * @param challengeType DEVICE_BIO or FACE_VERIFY
     * @return Challenge response with challengeId and data to sign
     */
    public SmartOtpChallengeResponse generateChallenge(String userId, String transactionId, String challengeType) {
        log.info("Generating Smart OTP challenge for userId: {} transactionId: {} type: {}", 
                userId, transactionId, challengeType);

        SmartOtpChallengeRequest request = SmartOtpChallengeRequest.builder()
                .userId(userId)
                .transactionId(transactionId)
                .challengeType(challengeType)
                .build();

        try {
            SmartOtpChallengeResponse response = webClientBuilder.build()
                    .post()
                    .uri(userServiceUrl + "/smart-otp/internal/challenge")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SmartOtpChallengeResponse.class)
                    .block(TIMEOUT);

            if (response != null) {
                log.info("Challenge generated: challengeId={} type={}", 
                        response.getChallengeId(), response.getChallengeType());
                return response;
            }

            log.error("Null response from user-service challenge generation");
            return null;

        } catch (Exception e) {
            log.error("Failed to generate Smart OTP challenge: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Verify device signature for DEVICE_BIO challenge.
     * 
     * @param challengeId Challenge ID from generateChallenge
     * @param deviceId Device that signed the challenge
     * @param signatureBase64 Base64-encoded signature
     * @return Verification result
     */
    public SmartOtpVerifyResponse verifyDeviceSignature(String challengeId, String deviceId, String signatureBase64) {
        log.info("Verifying device signature for challengeId: {} deviceId: {}", challengeId, deviceId);

        SmartOtpVerifyDeviceRequest request = SmartOtpVerifyDeviceRequest.builder()
                .challengeId(challengeId)
                .deviceId(deviceId)
                .signatureBase64(signatureBase64)
                .build();

        try {
            SmartOtpVerifyResponse response = webClientBuilder.build()
                    .post()
                    .uri(userServiceUrl + "/smart-otp/internal/verify-device")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SmartOtpVerifyResponse.class)
                    .block(TIMEOUT);

            if (response != null) {
                log.info("Device verification result: valid={}", response.isValid());
                return response;
            }

            log.error("Null response from user-service device verification");
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Smart OTP service unavailable")
                    .build();

        } catch (Exception e) {
            log.error("Failed to verify device signature: {}", e.getMessage());
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Device verification failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get Smart OTP status for a user (what verification methods are available).
     * 
     * @param userId User to check
     * @return Status with hasDevice and hasFace flags
     */
    public SmartOtpStatusResponse getSmartOtpStatus(String userId) {
        log.info("Checking Smart OTP status for userId: {}", userId);

        try {
            SmartOtpStatusResponse response = webClientBuilder.build()
                    .get()
                    .uri(userServiceUrl + "/smart-otp/internal/status/" + userId)
                    .retrieve()
                    .bodyToMono(SmartOtpStatusResponse.class)
                    .block(TIMEOUT);

            if (response != null) {
                log.info("Smart OTP status for userId: {} - hasDevice={} hasFace={}", 
                        userId, response.isHasDevice(), response.isHasFace());
                return response;
            }

            return SmartOtpStatusResponse.builder()
                    .hasDevice(false)
                    .hasFace(false)
                    .deviceCount(0)
                    .build();

        } catch (Exception e) {
            log.error("Failed to check Smart OTP status: {}", e.getMessage());
            return SmartOtpStatusResponse.builder()
                    .hasDevice(false)
                    .hasFace(false)
                    .deviceCount(0)
                    .build();
        }
    }

    /**
     * Check if user has any Smart OTP verification method available.
     * Used to determine if SMART_OTP challenge can be issued.
     * 
     * @param userId User to check
     * @return true if user has at least device registered
     */
    public boolean isSmartOtpCapable(String userId) {
        SmartOtpStatusResponse status = getSmartOtpStatus(userId);
        // User can use Smart OTP if they have a registered device
        // Face is optional fallback for HIGH risk
        return status.isHasDevice();
    }
}
