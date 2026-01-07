package com.uit.userservice.service;

import com.uit.userservice.dto.smartotp.*;
import com.uit.userservice.entity.Device;
import com.uit.userservice.entity.User;
import com.uit.userservice.repository.DeviceRepository;
import com.uit.userservice.repository.UserRepository;
import com.uit.sharedkernel.audit.AuditEventDto;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Smart OTP Service - Vietnamese e-banking style verification.
 * 
 * Challenge Types:
 * - DEVICE_BIO: Device-bound verification (fingerprint/PIN unlocks device key to sign challenge)
 * - FACE_VERIFY: Face re-verification for high-risk transactions
 * 
 * Flow:
 * 1. Transaction service requests challenge via /internal/smart-otp/challenge
 * 2. This service generates a random challenge string, stores in Redis with TTL
 * 3. Client receives challenge, prompts biometric, signs/verifies
 * 4. Client submits signed challenge or face image
 * 5. This service verifies and returns result
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartOtpService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final FaceIdService faceIdService;
    private final StringRedisTemplate redisTemplate;
    private final AuditEventPublisher auditEventPublisher;

    private static final String CHALLENGE_KEY_PREFIX = "smart_otp:challenge:";
    private static final Duration CHALLENGE_TTL = Duration.ofSeconds(120); // 2 minutes
    private static final int CHALLENGE_LENGTH = 32;

    /**
     * Generate a challenge for Smart OTP verification.
     * Called by transaction-service when a transaction requires verification.
     * 
     * @param userId User ID
     * @param transactionId Transaction ID (for audit)
     * @param challengeType DEVICE_BIO or FACE_VERIFY
     * @return Challenge data for client
     */
    public SmartOtpChallengeResponse generateChallenge(
            String userId, 
            String transactionId, 
            String challengeType) {
        
        log.info("Generating {} challenge for user={} transaction={}", 
                challengeType, userId, transactionId);

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // For FACE_VERIFY, check if user has face registered
        if ("FACE_VERIFY".equals(challengeType)) {
            if (!Boolean.TRUE.equals(user.getIsFaceRegistered())) {
                log.warn("User {} requested FACE_VERIFY but no face registered", userId);
                throw new AppException(ErrorCode.BAD_REQUEST, 
                        "Face not registered. Please register your face first.");
            }
        }

        // For DEVICE_BIO, check if user has at least one active device
        if ("DEVICE_BIO".equals(challengeType)) {
            List<Device> devices = deviceRepository.findByUserId(userId);
            boolean hasActiveDevice = devices.stream()
                    .anyMatch(d -> Boolean.TRUE.equals(d.getIsActive()));
            if (!hasActiveDevice) {
                log.warn("User {} requested DEVICE_BIO but no active device", userId);
                throw new AppException(ErrorCode.BAD_REQUEST, 
                        "No registered device. Please register your device first.");
            }
        }

        // Generate random challenge
        String challengeId = UUID.randomUUID().toString();
        String challengeData = generateRandomChallenge();

        // Store in Redis with TTL
        String redisKey = CHALLENGE_KEY_PREFIX + challengeId;
        String redisValue = String.join("|", userId, transactionId, challengeType, challengeData);
        redisTemplate.opsForValue().set(redisKey, redisValue, CHALLENGE_TTL);

        log.info("Challenge generated: id={} type={} expires in {}s", 
                challengeId, challengeType, CHALLENGE_TTL.getSeconds());

        return SmartOtpChallengeResponse.builder()
                .challengeId(challengeId)
                .challengeData(challengeData)
                .challengeType(challengeType)
                .expiresInSeconds((int) CHALLENGE_TTL.getSeconds())
                .message(getChallengeMessage(challengeType))
                .build();
    }

    /**
     * Verify a device signature response.
     * Device signs the challenge with its private key (unlocked by biometric).
     */
    public SmartOtpVerifyResponse verifyDeviceSignature(
            String challengeId,
            String deviceId,
            String signatureBase64) {
        
        log.info("Verifying device signature: challengeId={} deviceId={}", challengeId, deviceId);

        // Retrieve challenge from Redis
        String redisKey = CHALLENGE_KEY_PREFIX + challengeId;
        String redisValue = redisTemplate.opsForValue().get(redisKey);
        
        if (redisValue == null) {
            log.warn("Challenge expired or not found: {}", challengeId);
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Challenge expired or invalid")
                    .build();
        }

        // Parse stored data: userId|transactionId|challengeType|challengeData
        String[] parts = redisValue.split("\\|", 4);
        if (parts.length != 4) {
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Invalid challenge data")
                    .build();
        }

        String userId = parts[0];
        String transactionId = parts[1];
        String challengeType = parts[2];
        String challengeData = parts[3];

        if (!"DEVICE_BIO".equals(challengeType)) {
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Wrong verification method for this challenge")
                    .build();
        }

        // Find device
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || !device.getUserId().equals(userId) || !Boolean.TRUE.equals(device.getIsActive())) {
            log.warn("Device not found or not owned by user: deviceId={} userId={}", deviceId, userId);
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Device not found or inactive")
                    .build();
        }

        // Verify signature
        boolean signatureValid;
        try {
            signatureValid = verifySignature(
                    device.getPublicKeyPem(), 
                    challengeData, 
                    signatureBase64);
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Signature verification failed")
                    .build();
        }

        if (signatureValid) {
            // Delete challenge (one-time use)
            redisTemplate.delete(redisKey);
            
            // Update device last seen
            device.setLastSeenAt(LocalDateTime.now());
            deviceRepository.save(device);

            log.info("Device signature verified: user={} device={} transaction={}", 
                    userId, deviceId, transactionId);

            // Audit: successful device verification
            publishAuditEvent("SMART_OTP_DEVICE_VERIFY_SUCCESS", userId, transactionId, 
                    Map.of("deviceId", deviceId, "challengeId", challengeId));

            return SmartOtpVerifyResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .transactionId(transactionId)
                    .message("Device verification successful")
                    .build();
        } else {
            log.warn("Invalid signature for challenge: {}", challengeId);
            
            // Audit: failed device verification (potential attack)
            publishAuditEvent("SMART_OTP_DEVICE_VERIFY_FAILED", userId, transactionId, 
                    Map.of("deviceId", deviceId, "challengeId", challengeId, "reason", "invalid_signature"));
            
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Invalid signature")
                    .build();
        }
    }

    /**
     * Verify face for a challenge.
     * Delegates to existing FaceIdService.
     */
    public SmartOtpVerifyResponse verifyFace(
            String challengeId,
            List<MultipartFile> faceImages) {
        
        log.info("Verifying face for challenge: {}", challengeId);

        // Retrieve challenge from Redis
        String redisKey = CHALLENGE_KEY_PREFIX + challengeId;
        String redisValue = redisTemplate.opsForValue().get(redisKey);
        
        if (redisValue == null) {
            log.warn("Challenge expired or not found: {}", challengeId);
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Challenge expired or invalid")
                    .build();
        }

        // Parse stored data
        String[] parts = redisValue.split("\\|", 4);
        if (parts.length != 4) {
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Invalid challenge data")
                    .build();
        }

        String userId = parts[0];
        String transactionId = parts[1];
        String challengeType = parts[2];

        if (!"FACE_VERIFY".equals(challengeType)) {
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message("Wrong verification method for this challenge")
                    .build();
        }

        // Delegate to FaceIdService
        try {
            faceIdService.verifyFace(userId, faceImages, transactionId);
            
            // Delete challenge (one-time use)
            redisTemplate.delete(redisKey);

            log.info("Face verified: user={} transaction={}", userId, transactionId);

            // Audit: successful face verification
            publishAuditEvent("SMART_OTP_FACE_VERIFY_SUCCESS", userId, transactionId, 
                    Map.of("challengeId", challengeId));

            return SmartOtpVerifyResponse.builder()
                    .valid(true)
                    .userId(userId)
                    .transactionId(transactionId)
                    .message("Face verification successful")
                    .build();

        } catch (AppException e) {
            log.warn("Face verification failed for challenge {}: {}", challengeId, e.getMessage());
            
            // Audit: failed face verification (potential attack)
            publishAuditEvent("SMART_OTP_FACE_VERIFY_FAILED", userId, transactionId, 
                    Map.of("challengeId", challengeId, "reason", e.getMessage()));
            
            return SmartOtpVerifyResponse.builder()
                    .valid(false)
                    .message(e.getMessage())
                    .build();
        }
    }

    /**
     * Check user's Smart OTP capabilities.
     * Returns what verification methods are available for this user.
     */
    public SmartOtpStatusResponse getStatus(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return SmartOtpStatusResponse.builder()
                    .faceRegistered(false)
                    .deviceRegistered(false)
                    .message("User not found")
                    .build();
        }

        List<Device> devices = deviceRepository.findByUserId(userId);
        boolean hasActiveDevice = devices.stream()
                .anyMatch(d -> Boolean.TRUE.equals(d.getIsActive()));

        return SmartOtpStatusResponse.builder()
                .faceRegistered(Boolean.TRUE.equals(user.getIsFaceRegistered()))
                .deviceRegistered(hasActiveDevice)
                .activeDeviceCount((int) devices.stream()
                        .filter(d -> Boolean.TRUE.equals(d.getIsActive()))
                        .count())
                .message(getStatusMessage(user.getIsFaceRegistered(), hasActiveDevice))
                .build();
    }

    // ==================== Private Helpers ====================

    private String generateRandomChallenge() {
        byte[] bytes = new byte[CHALLENGE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean verifySignature(String publicKeyPem, String data, String signatureBase64) throws Exception {
        byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
        PublicKey pk = parsePublicKeyPem(publicKeyPem);

        String algorithm = pk.getAlgorithm();
        Signature sig;
        if ("RSA".equalsIgnoreCase(algorithm)) {
            sig = Signature.getInstance("SHA256withRSA");
        } else if ("EC".equalsIgnoreCase(algorithm)) {
            sig = Signature.getInstance("SHA256withECDSA");
        } else {
            sig = Signature.getInstance("SHA256withRSA");
        }

        sig.initVerify(pk);
        sig.update(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return sig.verify(sigBytes);
    }

    private PublicKey parsePublicKeyPem(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);

        try {
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ignored) {
        }
        return KeyFactory.getInstance("EC").generatePublic(spec);
    }

    private String getChallengeMessage(String challengeType) {
        return switch (challengeType) {
            case "DEVICE_BIO" -> "Please verify with your fingerprint or PIN on your registered device.";
            case "FACE_VERIFY" -> "Please look at the camera to verify your identity.";
            default -> "Please complete verification.";
        };
    }

    private String getStatusMessage(Boolean faceRegistered, boolean deviceRegistered) {
        if (Boolean.TRUE.equals(faceRegistered) && deviceRegistered) {
            return "Smart OTP fully configured. Face and device verification available.";
        } else if (Boolean.TRUE.equals(faceRegistered)) {
            return "Face registered. Register a device for faster verification.";
        } else if (deviceRegistered) {
            return "Device registered. Register your face for high-value transactions.";
        } else {
            return "No Smart OTP configured. Register your face or device for secure transactions.";
        }
    }

    /**
     * Publish audit event for Smart OTP operations.
     */
    private void publishAuditEvent(String action, String userId, String transactionId, Map<String, String> details) {
        try {
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("user-service")
                    .entityType("SMART_OTP")
                    .entityId(transactionId)
                    .action(action)
                    .userId(userId)
                    .result(action.contains("SUCCESS") ? "SUCCESS" : "FAILURE")
                    .metadata(details)
                    .timestamp(LocalDateTime.now())
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event: {}", action, e);
            // Don't fail the operation if audit fails - log and continue
        }
    }
}
