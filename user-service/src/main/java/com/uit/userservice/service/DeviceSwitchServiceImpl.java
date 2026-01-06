package com.uit.userservice.service;

import com.uit.sharedkernel.constants.RabbitMQConstants;
import com.uit.sharedkernel.constants.RedisKeyConstants;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.userservice.dto.request.DeviceSwitchOtpRequest;
import com.uit.userservice.dto.request.VerifyDeviceSwitchOtpRequest;
import com.uit.userservice.dto.response.DeviceSwitchOtpResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.keycloak.KeycloakClient;
import com.uit.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceSwitchServiceImpl implements DeviceSwitchService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final KeycloakClient keycloakClient;

    @Override
    public DeviceSwitchOtpResponse sendDeviceSwitchOtp(DeviceSwitchOtpRequest request) {
        log.info("Sending device switch OTP for user: {}, newDeviceId: {}", 
                request.getUserId(), request.getNewDeviceId());

        // Get user from database
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isEmpty()) {
            log.error("User {} does not have a phone number", request.getUserId());
            return DeviceSwitchOtpResponse.builder()
                    .success(false)
                    .message("PHONE_NUMBER_NOT_FOUND")
                    .build();
        }

        // Check rate limiting (max 3 OTP requests per 5 minutes)
        String rateLimitKey = RedisKeyConstants.DEVICE_SWITCH_ATTEMPTS_PREFIX + request.getUserId();
        String attempts = redisTemplate.opsForValue().get(rateLimitKey);
        
        if (attempts != null && Integer.parseInt(attempts) >= RedisKeyConstants.MAX_OTP_ATTEMPTS) {
            log.warn("Rate limit exceeded for user: {}", request.getUserId());
            return DeviceSwitchOtpResponse.builder()
                    .success(false)
                    .message("TOO_MANY_ATTEMPTS")
                    .build();
        }

        // Generate 6-digit OTP
        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        
        log.warn("DEVICE SWITCH OTP for user {}: {}", request.getUserId(), otp);
        
        // Store OTP in Redis with 5-minute expiry
        String otpKey = RedisKeyConstants.DEVICE_SWITCH_OTP_PREFIX + request.getUserId();
        redisTemplate.opsForValue().set(
                otpKey, 
                otp, 
                RedisKeyConstants.OTP_EXPIRY_MINUTES, 
                TimeUnit.MINUTES
        );

        // Store pending device switch info
        String pendingKey = RedisKeyConstants.DEVICE_SWITCH_PENDING_PREFIX + request.getUserId();
        redisTemplate.opsForValue().set(
                pendingKey,
                request.getNewDeviceId(),
                RedisKeyConstants.OTP_EXPIRY_MINUTES,
                TimeUnit.MINUTES
        );

        // Initialize/increment attempt counter
        if (attempts == null) {
            redisTemplate.opsForValue().set(
                    rateLimitKey, 
                    "1", 
                    RedisKeyConstants.OTP_EXPIRY_MINUTES, 
                    TimeUnit.MINUTES
            );
        } else {
            redisTemplate.opsForValue().increment(rateLimitKey);
        }

        // Send OTP via RabbitMQ to notification-service
        try {
            Map<String, Object> otpMessage = Map.of(
                    "phoneNumber", user.getPhoneNumber(),
                    "otpCode", otp,
                    "userId", request.getUserId()
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.TRANSACTION_EXCHANGE,
                    RabbitMQConstants.DEVICE_SWITCH_OTP_ROUTING_KEY,
                    otpMessage
            );

            log.info("Device switch OTP sent to RabbitMQ for user: {}, phone: {}", 
                    request.getUserId(), user.getPhoneNumber());

            return DeviceSwitchOtpResponse.builder()
                    .success(true)
                    .message("OTP_SENT")
                    .build();

        } catch (Exception e) {
            log.error("Failed to send device switch OTP to RabbitMQ", e);
            return DeviceSwitchOtpResponse.builder()
                    .success(false)
                    .message("OTP_SEND_FAILED")
                    .build();
        }
    }

    @Override
    public DeviceSwitchOtpResponse verifyDeviceSwitchOtp(VerifyDeviceSwitchOtpRequest request) {
        log.info("Verifying device switch OTP for user: {}", request.getUserId());

        String otpKey = RedisKeyConstants.DEVICE_SWITCH_OTP_PREFIX + request.getUserId();
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            log.warn("No OTP found for user: {} (expired or not sent)", request.getUserId());
            return DeviceSwitchOtpResponse.builder()
                    .success(false)
                    .message("OTP_EXPIRED")
                    .build();
        }

        if (!storedOtp.equals(request.getOtpCode())) {
            log.warn("Invalid OTP for user: {}", request.getUserId());
            return DeviceSwitchOtpResponse.builder()
                    .success(false)
                    .message("OTP_INVALID")
                    .build();
        }

        // OTP is valid - Revoke all Keycloak sessions for this user
        try {
            keycloakClient.revokeAllUserSessions(request.getUserId());
            log.info("Revoked all Keycloak sessions for user: {}", request.getUserId());
        } catch (Exception e) {
            log.error("Failed to revoke Keycloak sessions for user: {}", request.getUserId(), e);
            // Continue even if session revocation fails
        }

        // Clean up Redis keys
        String pendingKey = RedisKeyConstants.DEVICE_SWITCH_PENDING_PREFIX + request.getUserId();
        String rateLimitKey = RedisKeyConstants.DEVICE_SWITCH_ATTEMPTS_PREFIX + request.getUserId();
        
        redisTemplate.delete(otpKey);
        redisTemplate.delete(pendingKey);
        redisTemplate.delete(rateLimitKey);

        log.info("Device switch OTP verified successfully for user: {}", request.getUserId());

        return DeviceSwitchOtpResponse.builder()
                .success(true)
                .message("OTP_VERIFIED")
                .build();
    }
}
