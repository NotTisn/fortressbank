package com.uit.transactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OTPService
 * Tests OTP generation, verification, expiration, and attempt limits
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OTPService Unit Tests")
class OTPServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private OTPService otpService;

    private UUID testTransactionId;
    private String testPhoneNumber;
    private static final String OTP_KEY_PREFIX = "otp:transaction:";
    private static final String OTP_ATTEMPTS_KEY_PREFIX = "otp:transaction:attempts:";

    @BeforeEach
    void setUp() {
        testTransactionId = UUID.randomUUID();
        testPhoneNumber = "+84901234567";
    }

    @Test
    @DisplayName("generateOTP() should return 6-digit OTP code")
    void testGenerateOTP() {
        // When
        String otp = otpService.generateOTP();

        // Then
        assertThat(otp)
                .isNotNull()
                .hasSize(6)
                .matches("\\d{6}");
    }

    @Test
    @DisplayName("saveOTP() should store OTP data in Redis with expiry")
    void testSaveOTP() {
        // Given
        String otpCode = "123456";
        String key = OTP_KEY_PREFIX + testTransactionId;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        otpService.saveOTP(testTransactionId, otpCode, testPhoneNumber);

        // Then
        verify(valueOperations).set(eq(key), any(OTPService.OTPData.class), eq(Duration.ofSeconds(90)));
        verify(valueOperations).set(eq(attemptsKey), eq(0), eq(Duration.ofSeconds(90)));
    }

    @Test
    @DisplayName("verifyOTP() should return success for valid OTP")
    void testVerifyOTP_ValidOTP() {
        // Given
        String validOtp = "123456";
        String key = OTP_KEY_PREFIX + testTransactionId;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;
        
        OTPService.OTPData otpData = new OTPService.OTPData(validOtp, testPhoneNumber, System.currentTimeMillis());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(attemptsKey)).thenReturn(1L);
        when(valueOperations.get(key)).thenReturn(otpData);

        // When
        OTPService.OTPVerificationResult result = otpService.verifyOTP(testTransactionId, validOtp);

        // Then
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("OTP verified successfully");
        verify(redisTemplate).delete(key);
        verify(redisTemplate).delete(attemptsKey);
    }

    @Test
    @DisplayName("verifyOTP() should return invalid for wrong OTP code")
    void testVerifyOTP_InvalidOTP() {
        // Given
        String validOtp = "123456";
        String invalidOtp = "999999";
        String key = OTP_KEY_PREFIX + testTransactionId;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;
        
        OTPService.OTPData otpData = new OTPService.OTPData(validOtp, testPhoneNumber, System.currentTimeMillis());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(attemptsKey)).thenReturn(1L);
        when(valueOperations.get(key)).thenReturn(otpData);

        // When
        OTPService.OTPVerificationResult result = otpService.verifyOTP(testTransactionId, invalidOtp);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Invalid OTP code");
        assertThat(result.getRemainingAttempts()).isEqualTo(2);
        verify(redisTemplate, never()).delete(key);
    }

    @Test
    @DisplayName("verifyOTP() should return expired when OTP not found in Redis")
    void testVerifyOTP_ExpiredOTP() {
        // Given
        String otpCode = "123456";
        String key = OTP_KEY_PREFIX + testTransactionId;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(attemptsKey)).thenReturn(1L);
        when(valueOperations.get(key)).thenReturn(null);

        // When
        OTPService.OTPVerificationResult result = otpService.verifyOTP(testTransactionId, otpCode);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("OTP has expired");
        assertThat(result.getRemainingAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("verifyOTP() should fail when max attempts exceeded")
    void testVerifyOTP_MaxAttemptsExceeded() {
        // Given
        String otpCode = "123456";
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(attemptsKey)).thenReturn(4L); // 4th attempt

        // When
        OTPService.OTPVerificationResult result = otpService.verifyOTP(testTransactionId, otpCode);

        // Then
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Maximum OTP attempts exceeded");
        assertThat(result.getRemainingAttempts()).isEqualTo(0);
        verify(redisTemplate).delete(OTP_KEY_PREFIX + testTransactionId);
        verify(redisTemplate).delete(attemptsKey);
    }

    @Test
    @DisplayName("deleteOTP() should remove OTP and attempts from Redis")
    void testDeleteOTP() {
        // Given
        String key = OTP_KEY_PREFIX + testTransactionId;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;

        // When
        otpService.deleteOTP(testTransactionId);

        // Then
        verify(redisTemplate).delete(key);
        verify(redisTemplate).delete(attemptsKey);
    }

    @Test
    @DisplayName("otpExists() should return true when OTP exists in Redis")
    void testOtpExists_WhenExists() {
        // Given
        String key = OTP_KEY_PREFIX + testTransactionId;
        when(redisTemplate.hasKey(key)).thenReturn(true);

        // When
        boolean exists = otpService.otpExists(testTransactionId);

        // Then
        assertThat(exists).isTrue();
        verify(redisTemplate).hasKey(key);
    }

    @Test
    @DisplayName("otpExists() should return false when OTP does not exist")
    void testOtpExists_WhenNotExists() {
        // Given
        String key = OTP_KEY_PREFIX + testTransactionId;
        when(redisTemplate.hasKey(key)).thenReturn(false);

        // When
        boolean exists = otpService.otpExists(testTransactionId);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("getOtpData() should return OTP data from Redis")
    void testGetOtpData() {
        // Given
        String otpCode = "123456";
        String key = OTP_KEY_PREFIX + testTransactionId;
        OTPService.OTPData expectedData = new OTPService.OTPData(otpCode, testPhoneNumber, System.currentTimeMillis());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(expectedData);

        // When
        OTPService.OTPData result = otpService.getOtpData(testTransactionId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOtpCode()).isEqualTo(otpCode);
        assertThat(result.getPhoneNumber()).isEqualTo(testPhoneNumber);
    }

    @Test
    @DisplayName("verifyOTP() should handle null increment result")
    void testVerifyOTP_NullIncrementResult() {
        // Given
        String validOtp = "123456";
        String key = OTP_KEY_PREFIX + testTransactionId;
        String attemptsKey = OTP_ATTEMPTS_KEY_PREFIX + testTransactionId;
        
        OTPService.OTPData otpData = new OTPService.OTPData(validOtp, testPhoneNumber, System.currentTimeMillis());
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(attemptsKey)).thenReturn(null);
        when(valueOperations.get(key)).thenReturn(otpData);

        // When
        OTPService.OTPVerificationResult result = otpService.verifyOTP(testTransactionId, validOtp);

        // Then
        assertThat(result.isSuccess()).isTrue();
    }
}
