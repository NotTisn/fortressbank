package com.uit.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.sharedkernel.constants.RabbitMQConstants;
import com.uit.sharedkernel.event.UserCreatedEvent;
import com.uit.sharedkernel.outbox.OutboxEvent;
import com.uit.sharedkernel.outbox.OutboxEventStatus;
import com.uit.sharedkernel.outbox.repository.OutboxEventRepository;
import com.uit.userservice.client.AccountClient;
import com.uit.userservice.client.CardClient;
import com.uit.userservice.client.CreateAccountInternalRequest;
import com.uit.userservice.dto.request.*;
import com.uit.userservice.dto.response.AccountDto;
import com.uit.userservice.dto.response.CardDto;
import com.uit.userservice.dto.response.FaceRegistrationResult;
import com.uit.userservice.dto.response.OtpResponse;
import com.uit.userservice.dto.response.TokenResponse;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.dto.response.ValidationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import com.uit.userservice.entity.User;
import com.uit.userservice.keycloak.KeycloakClient;
import com.uit.userservice.repository.UserRepository;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final KeycloakClient keycloakClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    private final EmailService emailService;
    private final AccountClient accountClient;
    private final FaceIdService faceIdService;

    // ==================== NEW MULTI-STEP REGISTRATION FLOW ====================

    @Override
    public OtpResponse validateAndSendOtp(ValidateRegistrationRequest request) {
        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_EXISTS);
        }

        // Check if phone number already exists
        if (userRepository.findByPhoneNumber(request.getPhoneNumber()).isPresent()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PHONE_NUMBER_ALREADY_EXISTS");
        }

        // Check if citizen ID already exists
        if (userRepository.findByCitizenId(request.getCitizenId()).isPresent()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "CITIZEN_ID_ALREADY_EXISTS");
        }

        // Store validation data in Redis temporarily (valid for 10 minutes)
        String validationKey = "registration:validate:" + request.getEmail();
        redisTemplate.opsForValue().set(
                validationKey,
                request.getCitizenId() + "|" + request.getPhoneNumber(),
                10,
                TimeUnit.MINUTES
        );

        // Generate 6-digit OTP
        String otp = String.format("%06d", (int)(Math.random() * 1000000));

        // Store OTP in Redis (valid for 5 minutes)
        String otpKey = "otp:" + request.getEmail();
        redisTemplate.opsForValue().set(otpKey, otp, 5, TimeUnit.MINUTES);

        // Send OTP via email
        try {
            emailService.sendOtpEmail(request.getEmail(), otp, 5);
            log.info("OTP sent successfully to {}", request.getEmail());
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", request.getEmail(), e.getMessage());
            // Clean up Redis if email fails
            redisTemplate.delete(otpKey);
            redisTemplate.delete(validationKey);
            throw new AppException(ErrorCode.NOTIFICATION_SERVICE_FAILED, "Failed to send OTP email");
        }

        return new OtpResponse(true, "OTP_SENT_SUCCESSFULLY");
    }

    @Override
    public ValidationResponse verifyOtp(VerifyOtpRequest request) {
        // Get OTP from Redis
        String otpKey = "otp:" + request.getEmail();
        String storedOtp;
        try {
            storedOtp = redisTemplate.opsForValue().get(otpKey);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "REDIS_ERROR");
        }

        if (storedOtp == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "OTP_EXPIRED");
        }

        if (!storedOtp.equals(request.getOtp())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "OTP_INVALID");
        }

        // Mark OTP as verified in Redis (delete OTP, set verified flag)
        redisTemplate.delete(otpKey);
        String verifiedKey = "otp:verified:" + request.getEmail();
        redisTemplate.opsForValue().set(verifiedKey, "true", 30, TimeUnit.MINUTES);

        return new ValidationResponse(true, "OTP_VERIFIED_SUCCESSFULLY");
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        // Create user in transaction (atomic operation)
        User user = createUserInTransaction(request);

        // Post-registration tasks (non-transactional, can fail without affecting user creation)

        // Send welcome email (async, non-blocking)
        try {
            emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.warn("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
            // Don't fail registration if welcome email fails
        }

        // Create account for user automatically
        try {
            log.info("Creating account for user {} with accountNumberType {}", user.getId(), request.getAccountNumberType());
            CreateAccountInternalRequest accountRequest = CreateAccountInternalRequest.builder()
                    .accountNumberType(request.getAccountNumberType())
                    .phoneNumber(request.getPhoneNumber())
                    .pin(request.getPin())
                    .build();

            AccountDto account = accountClient.createAccountForUser(user.getId(), accountRequest, user.getFullName()).getData();
            log.info("Account created successfully for user {} with accountNumber {} and PIN set",
                    user.getId(), account.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to create account for user {}: {}", user.getId(), e.getMessage(), e);
        }

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getCitizenId(),
                user.getDob(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getIsFaceRegistered()
        );
    }

    @Transactional
    private User createUserInTransaction(RegisterRequest request) {
        // Verify OTP was completed for this email
        String verifiedKey = "otp:verified:" + request.getEmail();
        String isVerified;
        try {
            isVerified = redisTemplate.opsForValue().get(verifiedKey);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "REDIS_ERROR");
        }

        log.debug("Register: verifiedKey='{}' value='{}'", verifiedKey, isVerified);

        if (isVerified == null) {
            log.warn("OTP not verified for email={}", request.getEmail());
            throw new AppException(ErrorCode.BAD_REQUEST, "OTP_NOT_VERIFIED");
        }

        // Ensure validation data exists and matches provided citizenId/phoneNumber
        String validationKey = "registration:validate:" + request.getEmail();
        String validationData;
        try {
            validationData = redisTemplate.opsForValue().get(validationKey);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "REDIS_ERROR");
        }
        log.debug("Register: validationKey='{}' value='{}'", validationKey, validationData);
        if (validationData == null) {
            log.warn("Validation data not found for email={}", request.getEmail());
            throw new AppException(ErrorCode.BAD_REQUEST, "VALIDATION_NOT_FOUND");
        }
        String[] parts = validationData.split("\\|");
        String storedCitizenId = parts.length > 0 ? parts[0] : null;
        String storedPhone = parts.length > 1 ? parts[1] : null;

        if (!request.getCitizenId().equals(storedCitizenId) || !request.getPhoneNumber().equals(storedPhone)) {
            log.warn("Validation mismatch for email={}, storedCitizenId={}, storedPhone={}", request.getEmail(), storedCitizenId, storedPhone);
            throw new AppException(ErrorCode.BAD_REQUEST, "VALIDATION_MISMATCH");
        }

        // Check uniqueness
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AppException(ErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.EMAIL_EXISTS);
        }

        // 1. Create user in Keycloak
        String keycloakUserId = keycloakClient.createUser(
                request.getUsername(),
                request.getEmail(),
                request.getFullName(),
                request.getPassword()
        );

        // 2. Create user profile in local DB
        User user = new User();
        user.setId(keycloakUserId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setDob(request.getDob());
        user.setCitizenId(request.getCitizenId());
        user.setPhoneNumber(request.getPhoneNumber());

        user = userRepository.save(user); // Assign back to get the persisted entity with createdAt

        // 3. Create outbox event
        UserCreatedEvent eventPayload = UserCreatedEvent.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();

        try {
            // Save to Outbox table
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("USER")
                    .aggregateId(user.getId())
                    .eventType("UserCreated")
                    .exchange(RabbitMQConstants.INTERNAL_EXCHANGE)
                    .routingKey(RabbitMQConstants.USER_CREATED_ROUTING_KEY)
                    .payload(objectMapper.writeValueAsString(eventPayload))
                    .status(OutboxEventStatus.PENDING)
                    .build();

            outboxEventRepository.save(outboxEvent);

            // Try to publish immediately to RabbitMQ (best effort, non-blocking)
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConstants.INTERNAL_EXCHANGE,
                        RabbitMQConstants.USER_CREATED_ROUTING_KEY,
                        objectMapper.writeValueAsString(eventPayload)
                );
                log.info("Published UserCreated event for userId {} to RabbitMQ", user.getId());
            } catch (Exception e) {
                log.warn("Failed to publish UserCreated event immediately for userId {}: {}", user.getId(), e.getMessage());
            }
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Failed to create outbox event");
        }

        // Cleanup Redis keys used in registration
        redisTemplate.delete(validationKey);
        redisTemplate.delete(verifiedKey);

        return user;
    }

    // ==================== OTHER AUTH METHODS ====================

    @Override
    public TokenResponse login(LoginRequest request) {
        // Keycloak sẽ verify username/password
        return keycloakClient.loginWithPassword(request.username(), request.password());
    }

    @Override
    public void logout(LogoutRequest request) {
        keycloakClient.logout(request.refreshToken());
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        return keycloakClient.refreshToken(request.refreshToken());
    }

    @Override
    public void changePassword(ChangePasswordRequest request, String userId, String username) {
        // verify old password
        try {
            keycloakClient.verifyPassword(username, request.oldPassword());
        } catch (AppException ex) {
            throw new AppException(ErrorCode.FORBIDDEN, "Old password is incorrect");
        }

        // reset password
        keycloakClient.resetPassword(userId, request.newPassword());
    }

    // ==================== FORGOT PASSWORD FLOW ====================

    @Override
    public com.uit.userservice.dto.response.ForgotPasswordOtpResponse forgotPasswordSendOtp(ForgotPasswordSendOtpRequest request) {
        String phoneNumber = request.getPhoneNumber();

        // 1. CHECK RATE LIMITING (prevent abuse)
        String rateLimitKey = "forgot-password:rate-limit:" + phoneNumber;
        String rateLimitValue = redisTemplate.opsForValue().get(rateLimitKey);

        if (rateLimitValue != null) {
            int attempts = Integer.parseInt(rateLimitValue);
            if (attempts >= 3) { // Max 3 OTP requests per hour
                log.warn("Rate limit exceeded for phone: {}", phoneNumber);
                // Return success to prevent enumeration
                return new com.uit.userservice.dto.response.ForgotPasswordOtpResponse(true, "OTP_SENT_SUCCESSFULLY", 300);
            }
            redisTemplate.opsForValue().increment(rateLimitKey);
        } else {
            redisTemplate.opsForValue().set(rateLimitKey, "1", 1, TimeUnit.HOURS);
        }

        // 2. CHECK IF USER EXISTS (internally, don't reveal to caller)
        java.util.Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
        if (userOpt.isEmpty()) {
            log.warn("Forgot password attempt for non-existent phone: {}", phoneNumber);
            // CRITICAL: Return success to prevent enumeration attack
            // Don't reveal that phone doesn't exist
            return new com.uit.userservice.dto.response.ForgotPasswordOtpResponse(true, "OTP_SENT_SUCCESSFULLY", 300);
        }

        // 3. GENERATE 6-DIGIT OTP
        String otp = String.format("%06d", (int)(Math.random() * 1000000));

        // 4. STORE OTP IN REDIS (5 minutes TTL)
        String otpKey = "forgot-password:otp:" + phoneNumber;
        redisTemplate.opsForValue().set(otpKey, otp, 5, TimeUnit.MINUTES);

        // 5. INITIALIZE ATTEMPT COUNTER (3 max attempts)
        String attemptsKey = "forgot-password:attempts:" + phoneNumber;
        redisTemplate.opsForValue().set(attemptsKey, "0", 5, TimeUnit.MINUTES);

        // 6. SEND OTP VIA RABBITMQ (async to notification-service)
        try {
            java.util.Map<String, Object> otpMessage = java.util.Map.of(
                "phoneNumber", phoneNumber,
                "otpCode", otp
            );

            rabbitTemplate.convertAndSend(
                RabbitMQConstants.TRANSACTION_EXCHANGE,
                RabbitMQConstants.FORGOT_PASSWORD_OTP_ROUTING_KEY,
                otpMessage
            );

            log.info("Forgot password OTP sent to RabbitMQ for phone: {}", phoneNumber);
        } catch (Exception e) {
            log.error("Failed to send OTP to RabbitMQ for phone {}: {}", phoneNumber, e.getMessage());
            // Clean up Redis if RabbitMQ fails
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            throw new AppException(ErrorCode.NOTIFICATION_SERVICE_FAILED, "Failed to send OTP");
        }

        return new com.uit.userservice.dto.response.ForgotPasswordOtpResponse(true, "OTP_SENT_SUCCESSFULLY", 300);
    }

    @Override
    public com.uit.userservice.dto.response.ForgotPasswordVerifyResponse forgotPasswordVerifyOtp(ForgotPasswordVerifyOtpRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String providedOtp = request.getOtp();

        // 1. GET STORED OTP FROM REDIS
        String otpKey = "forgot-password:otp:" + phoneNumber;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            throw new AppException(ErrorCode.OTP_EXPIRED_OR_NOT_FOUND);
        }

        // 2. CHECK ATTEMPT COUNTER
        String attemptsKey = "forgot-password:attempts:" + phoneNumber;
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= 3) {
            // Max attempts reached, delete OTP
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            throw new AppException(ErrorCode.MAX_OTP_ATTEMPTS_EXCEEDED);
        }

        // 3. VERIFY OTP
        if (!storedOtp.equals(providedOtp)) {
            // Increment attempt counter
            redisTemplate.opsForValue().increment(attemptsKey);
            throw new AppException(ErrorCode.INVALID_OTP);
        }

        // 4. OTP VERIFIED - GENERATE VERIFICATION TOKEN
        // Use UUID for unpredictability
        String verificationToken = java.util.UUID.randomUUID().toString();

        // 5. STORE VERIFICATION TOKEN (10 minutes TTL for password reset)
        String verifiedKey = "forgot-password:verified:" + phoneNumber;
        redisTemplate.opsForValue().set(verifiedKey, verificationToken, 10, TimeUnit.MINUTES);

        // 6. CLEANUP OTP AND ATTEMPTS
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);

        log.info("OTP verified successfully for phone: {}", phoneNumber);

        return new com.uit.userservice.dto.response.ForgotPasswordVerifyResponse(
            true,
            "OTP_VERIFIED_SUCCESSFULLY",
            verificationToken
        );
    }

    @Override
    public com.uit.userservice.dto.response.ForgotPasswordResetResponse forgotPasswordReset(ForgotPasswordResetRequest request) {
        String phoneNumber = request.getPhoneNumber();
        String providedToken = request.getVerificationToken();
        String newPassword = request.getNewPassword();

        // 1. VERIFY VERIFICATION TOKEN
        String verifiedKey = "forgot-password:verified:" + phoneNumber;
        String storedToken = redisTemplate.opsForValue().get(verifiedKey);

        if (storedToken == null) {
            throw new AppException(ErrorCode.VERIFICATION_TOKEN_EXPIRED);
        }

        if (!storedToken.equals(providedToken)) {
            throw new AppException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        // 2. FIND USER BY PHONE NUMBER
        User user = userRepository.findByPhoneNumber(phoneNumber)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 3. RESET PASSWORD IN KEYCLOAK
        try {
            keycloakClient.resetPassword(user.getId(), newPassword);
            log.info("Password reset successfully for user: {} (phone: {})", user.getId(), phoneNumber);
        } catch (Exception e) {
            log.error("Failed to reset password in Keycloak for user {}: {}", user.getId(), e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "Failed to reset password");
        }

        // 4. CLEANUP VERIFICATION TOKEN
        redisTemplate.delete(verifiedKey);

        // 5. SEND CONFIRMATION SMS (best-effort, don't fail if notification fails)
        try {
            java.util.Map<String, Object> confirmationMessage = java.util.Map.of(
                "phoneNumber", phoneNumber,
                "otpCode", "Your FortressBank password has been reset successfully"  // Reuse OTP field for message
            );

            rabbitTemplate.convertAndSend(
                RabbitMQConstants.TRANSACTION_EXCHANGE,
                RabbitMQConstants.FORGOT_PASSWORD_OTP_ROUTING_KEY,
                confirmationMessage
            );

            log.info("Password reset confirmation sent to RabbitMQ for phone: {}", phoneNumber);
        } catch (Exception e) {
            log.warn("Failed to send password reset confirmation SMS: {}", e.getMessage());
            // Don't fail the reset if notification fails
        }

        return new com.uit.userservice.dto.response.ForgotPasswordResetResponse(true, "PASSWORD_RESET_SUCCESSFULLY");
    }

    @Override
    public FaceRegistrationResult registerFacePublic(String userId, List<MultipartFile> files) {
        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        log.info("Public face registration request for user: {}", userId);

        // Delegate to FaceIdService
        return faceIdService.registerFace(userId, files);
    }
}
