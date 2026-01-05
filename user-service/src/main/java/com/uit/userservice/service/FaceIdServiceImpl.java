// Path: com.uit.userservice.service.impl.FaceIdServiceImpl.java
package com.uit.userservice.service;

import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.userservice.client.FaceIdClient;
import com.uit.userservice.client.TransactionClient;
import com.uit.userservice.dto.request.ConfirmFaceAuthRequest;
import com.uit.userservice.dto.response.FaceRegistrationResult;
import com.uit.userservice.dto.response.FaceVerificationResult;
import com.uit.userservice.dto.response.ai.FaceRegisterResponse;
import com.uit.userservice.dto.response.ai.FaceVerifyResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.repository.UserRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceIdServiceImpl implements FaceIdService {

    private final FaceIdClient faceIdClient;
    private final UserRepository userRepository;
    private final TransactionClient transactionClient;

    @Override
    @Transactional
    public FaceRegistrationResult registerFace(String userId, List<MultipartFile> files) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIsFaceRegistered())) {
            log.warn("User {} already has face registered", userId);
            throw new AppException(ErrorCode.INVALID_FACE_DATA, "Face already registered for this user");
        }

        try {
            log.info("Sending face registration request to AI Service for user {}", userId);
            FaceRegisterResponse aiResponse = faceIdClient.registerFace(userId, files);

            if (aiResponse.isSuccess()) {
                // Registration successful - update user
                user.setIsFaceRegistered(true);
                userRepository.save(user);

                log.info("Face registration successful for user {}, samples: {}",
                        userId, aiResponse.getSampleSize());

                return new FaceRegistrationResult(
                        true,
                        "Face registered successfully",
                        null,
                        aiResponse.getAvgLivenessScore(),
                        aiResponse.getSampleSize()
                );
            } else {
                // Registration failed (spoof detected, poor quality, etc.)
                log.warn("Face registration failed for user {}: {} - {}",
                        userId, aiResponse.getReason(), aiResponse.getMessage());

                throw new AppException(ErrorCode.INVALID_FACE_DATA, aiResponse.getMessage());
            }

        } catch (FeignException.ServiceUnavailable e) {
            log.error("AI Service is unavailable: {}", e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Face recognition service is temporarily unavailable. Please try again later.");

        } catch (FeignException e) {
            log.error("AI Service error (status {}): {}", e.status(), e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Error communicating with face recognition service");

        } catch (Exception e) {
            log.error("Unexpected error during face registration for user {}: {}", userId, e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Unexpected error during face registration. Please try again.");
        }
    }

    @Override
    @Transactional
    public FaceRegistrationResult updateFace(String userId, List<MultipartFile> files) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsFaceRegistered())) {
            log.warn("User {} attempted to update face without initial registration", userId);
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "No face registered yet. Please register your face first.");
        }

        try {
            log.info("Sending face update request to AI Service for user {}", userId);
            FaceRegisterResponse aiResponse = faceIdClient.registerFace(userId, files);

            if (aiResponse.isSuccess()) {
                log.info("Face update successful for user {}, samples: {}",
                        userId, aiResponse.getSampleSize());

                return new FaceRegistrationResult(
                        true,
                        "Face updated successfully",
                        null,
                        aiResponse.getAvgLivenessScore(),
                        aiResponse.getSampleSize()
                );
            } else {
                log.warn("Face update failed for user {}: {} - {}",
                        userId, aiResponse.getReason(), aiResponse.getMessage());

                throw new AppException(ErrorCode.INVALID_FACE_DATA, aiResponse.getMessage());
            }

        } catch (FeignException.ServiceUnavailable e) {
            log.error("AI Service is unavailable: {}", e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Face recognition service is temporarily unavailable. Please try again later.");

        } catch (FeignException e) {
            log.error("AI Service error (status {}): {}", e.status(), e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Error communicating with face recognition service");

        } catch (Exception e) {
            log.error("Unexpected error during face update for user {}: {}", userId, e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Unexpected error during face update. Please try again.");
        }
    }

    @Override
    public FaceVerificationResult verifyFace(String userId, List<MultipartFile> files, String transactionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsFaceRegistered())) {
            log.warn("User {} attempted verification without registered face", userId);
            throw new AppException(ErrorCode.BAD_REQUEST, "Face not registered. Please register your face first.");
        }

        try {
            log.info("Sending face verification request to AI Service for user {}", userId);
            FaceVerifyResponse aiResponse = faceIdClient.verifyTransaction(userId, files);

            if (aiResponse.isSuccess()) {
                boolean isMatch = aiResponse.isMatch();

                log.info("Face verification completed for user {}: match={}, similarity={}",
                        userId, isMatch, aiResponse.getSimilarity());

                if (!isMatch) {
                    throw new AppException(ErrorCode.FACE_VERIFICATION_FAILED, "Face verification mismatch");
                }

                // If match is successful and we have a transactionId, notify transaction-service
                if (transactionId != null && !transactionId.isEmpty()) {
                    log.info("Face verified for transaction {}. Notifying transaction-service...", transactionId);
                    try {
                        ConfirmFaceAuthRequest confirmRequest = ConfirmFaceAuthRequest.builder()
                                .transactionId(UUID.fromString(transactionId))
                                .phoneNumber(user.getPhoneNumber()) // Send user's phone for OTP
                                .build();
                        
                        transactionClient.confirmFaceAuthSuccess(confirmRequest);
                        log.info("Successfully notified transaction-service for transaction {}", transactionId);
                    } catch (Exception e) {
                        log.error("Failed to notify transaction-service for transaction {}: {}", transactionId, e.getMessage());
                        // We don't necessarily want to fail the whole process if the notification fails, 
                        // but for high-security transactions, maybe we should.
                        // For now, let's just log it.
                    }
                }

                return new FaceVerificationResult(
                        true,
                        "Face verification successful",
                        null,
                        aiResponse.getSimilarity(),
                        aiResponse.getAvgLivenessScore()
                );
            } else {
                // Verification failed (spoof detected, no face found, etc.)
                log.warn("Face verification failed for user {}: {} - {}",
                        userId, aiResponse.getReason(), aiResponse.getMessage());

                throw new AppException(ErrorCode.INVALID_FACE_DATA, aiResponse.getMessage());
            }

        } catch (FeignException.ServiceUnavailable e) {
            log.error("AI Service is unavailable: {}", e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Face recognition service is temporarily unavailable. Please try again later.");

        } catch (FeignException e) {
            log.error("AI Service error (status {}): {}", e.status(), e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Error communicating with face recognition service");

        } catch (Exception e) {
            log.error("Unexpected error during face verification for user {}: {}", userId, e.getMessage(), e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                    "Unexpected error during face verification. Please try again.");
        }
    }
}