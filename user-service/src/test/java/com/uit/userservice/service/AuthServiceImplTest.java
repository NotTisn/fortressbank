package com.uit.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.outbox.repository.OutboxEventRepository;
import com.uit.userservice.client.AccountClient;
import com.uit.userservice.dto.request.*;
import com.uit.userservice.dto.response.OtpResponse;
import com.uit.userservice.dto.response.TokenResponse;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.dto.response.ValidationResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.keycloak.KeycloakClient;
import com.uit.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthServiceImpl
 * Tests authentication, OTP, and user registration flows
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Unit Tests")
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakClient keycloakClient;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private EmailService emailService;

    @Mock
    private AccountClient accountClient;

    @Mock
    private FaceIdService faceIdService;

    @InjectMocks
    private AuthServiceImpl authService;

    private String testEmail;
    private String testPhoneNumber;
    private String testCitizenId;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testPhoneNumber = "0123456789";
        testCitizenId = "123456789";

        // Mock RedisTemplate
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ===== validateAndSendOtp Tests =====

    @Test
    @DisplayName("validateAndSendOtp() should send OTP successfully when all validations pass")
    void testValidateAndSendOtp_Success() {
        // Given
        ValidateRegistrationRequest request = new ValidateRegistrationRequest(
                testEmail,
                testPhoneNumber,
                testCitizenId
        );

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(testPhoneNumber)).thenReturn(Optional.empty());
        when(userRepository.findByCitizenId(testCitizenId)).thenReturn(Optional.empty());

        // When
        OtpResponse result = authService.validateAndSendOtp(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.sent()).isTrue();
        assertThat(result.message()).isEqualTo("OTP_SENT_SUCCESSFULLY");

        verify(valueOperations).set(eq("registration:validate:" + testEmail), anyString(), eq(10L), eq(TimeUnit.MINUTES));
        verify(valueOperations).set(eq("otp:" + testEmail), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        verify(emailService).sendOtpEmail(eq(testEmail), anyString(), eq(5));
    }

    @Test
    @DisplayName("validateAndSendOtp() should throw exception when email already exists")
    void testValidateAndSendOtp_EmailExists() {
        // Given
        ValidateRegistrationRequest request = new ValidateRegistrationRequest(
                testEmail,
                testPhoneNumber,
                testCitizenId
        );

        User existingUser = new User();
        existingUser.setEmail(testEmail);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));

        // When & Then
        assertThatThrownBy(() -> authService.validateAndSendOtp(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Email already exists");

        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("validateAndSendOtp() should throw exception when phone number already exists")
    void testValidateAndSendOtp_PhoneExists() {
        // Given
        ValidateRegistrationRequest request = new ValidateRegistrationRequest(
                testEmail,
                testPhoneNumber,
                testCitizenId
        );

        User existingUser = new User();
        existingUser.setPhoneNumber(testPhoneNumber);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(testPhoneNumber)).thenReturn(Optional.of(existingUser));

        // When & Then
        assertThatThrownBy(() -> authService.validateAndSendOtp(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("PHONE_NUMBER_ALREADY_EXISTS");

        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("validateAndSendOtp() should throw exception when citizen ID already exists")
    void testValidateAndSendOtp_CitizenIdExists() {
        // Given
        ValidateRegistrationRequest request = new ValidateRegistrationRequest(
                testEmail,
                testPhoneNumber,
                testCitizenId
        );

        User existingUser = new User();
        existingUser.setCitizenId(testCitizenId);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(testPhoneNumber)).thenReturn(Optional.empty());
        when(userRepository.findByCitizenId(testCitizenId)).thenReturn(Optional.of(existingUser));

        // When & Then
        assertThatThrownBy(() -> authService.validateAndSendOtp(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("CITIZEN_ID_ALREADY_EXISTS");

        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("validateAndSendOtp() should clean up Redis when email sending fails")
    void testValidateAndSendOtp_EmailSendingFails() {
        // Given
        ValidateRegistrationRequest request = new ValidateRegistrationRequest(
                testEmail,
                testPhoneNumber,
                testCitizenId
        );

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(userRepository.findByPhoneNumber(testPhoneNumber)).thenReturn(Optional.empty());
        when(userRepository.findByCitizenId(testCitizenId)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Email service error")).when(emailService).sendOtpEmail(anyString(), anyString(), anyInt());

        // When & Then
        assertThatThrownBy(() -> authService.validateAndSendOtp(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Failed to send OTP email");

        verify(redisTemplate, times(2)).delete(anyString());
    }

    // ===== verifyOtp Tests =====

    @Test
    @DisplayName("verifyOtp() should verify OTP successfully")
    void testVerifyOtp_Success() {
        // Given
        String otp = "123456";
        VerifyOtpRequest request = new VerifyOtpRequest(testEmail, otp);

        when(valueOperations.get("otp:" + testEmail)).thenReturn(otp);

        // When
        ValidationResponse result = authService.verifyOtp(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isEqualTo("OTP_VERIFIED_SUCCESSFULLY");

        verify(redisTemplate).delete("otp:" + testEmail);
        verify(valueOperations).set(eq("otp:verified:" + testEmail), eq("true"), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("verifyOtp() should throw exception when OTP expired")
    void testVerifyOtp_OtpExpired() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "123456");

        when(valueOperations.get("otp:" + testEmail)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("OTP_EXPIRED");

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("verifyOtp() should throw exception when OTP invalid")
    void testVerifyOtp_InvalidOtp() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(testEmail, "123456");

        when(valueOperations.get("otp:" + testEmail)).thenReturn("654321");

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("OTP_INVALID");

        verify(redisTemplate, never()).delete(anyString());
    }

    // ===== register Tests =====

    @Test
    @DisplayName("register() should create user successfully")
    void testRegister_Success() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest(
                "johndoe",
                testEmail,
                "password123",
                "John Doe",
                LocalDate.of(1990, 1, 1),
                testCitizenId,
                testPhoneNumber,
                "PHONE",
                "1234"
        );

        String keycloakUserId = "keycloak-123";

        when(valueOperations.get("otp:verified:" + testEmail)).thenReturn("true");
        when(valueOperations.get("registration:validate:" + testEmail)).thenReturn(testCitizenId + "|" + testPhoneNumber);
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());
        when(keycloakClient.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn(keycloakUserId);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(keycloakUserId);
            return user;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        UserResponse result = authService.register(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.username()).isEqualTo("johndoe");
        assertThat(result.email()).isEqualTo(testEmail);

        verify(keycloakClient).createUser("johndoe", testEmail, "John Doe", "password123");
        verify(userRepository).save(any(User.class));
        verify(outboxEventRepository).save(any());
        verify(redisTemplate).delete("registration:validate:" + testEmail);
        verify(redisTemplate).delete("otp:verified:" + testEmail);
    }

    @Test
    @DisplayName("register() should throw exception when OTP not verified")
    void testRegister_OtpNotVerified() {
        // Given
        RegisterRequest request = new RegisterRequest(
                "johndoe",
                testEmail,
                "password123",
                "John Doe",
                LocalDate.of(1990, 1, 1),
                testCitizenId,
                testPhoneNumber,
                "PHONE",
                "1234"
        );

        when(valueOperations.get("otp:verified:" + testEmail)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("OTP_NOT_VERIFIED");

        verify(keycloakClient, never()).createUser(anyString(), anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() should throw exception when validation data not found")
    void testRegister_ValidationNotFound() {
        // Given
        RegisterRequest request = new RegisterRequest(
                "johndoe",
                testEmail,
                "password123",
                "John Doe",
                LocalDate.of(1990, 1, 1),
                testCitizenId,
                testPhoneNumber,
                "PHONE",
                "1234"
        );

        when(valueOperations.get("otp:verified:" + testEmail)).thenReturn("true");
        when(valueOperations.get("registration:validate:" + testEmail)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("VALIDATION_NOT_FOUND");

        verify(keycloakClient, never()).createUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("register() should throw exception when validation mismatch")
    void testRegister_ValidationMismatch() {
        // Given
        RegisterRequest request = new RegisterRequest(
                "johndoe",
                testEmail,
                "password123",
                "John Doe",
                LocalDate.of(1990, 1, 1),
                "different-citizen-id",
                testPhoneNumber,
                "PHONE",
                "1234"
        );

        when(valueOperations.get("otp:verified:" + testEmail)).thenReturn("true");
        when(valueOperations.get("registration:validate:" + testEmail)).thenReturn(testCitizenId + "|" + testPhoneNumber);

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("VALIDATION_MISMATCH");

        verify(keycloakClient, never()).createUser(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("register() should throw exception when username exists")
    void testRegister_UsernameExists() {
        // Given
        RegisterRequest request = new RegisterRequest(
                "johndoe",
                testEmail,
                "password123",
                "John Doe",
                LocalDate.of(1990, 1, 1),
                testCitizenId,
                testPhoneNumber,
                "PHONE",
                "1234"
        );

        User existingUser = new User();
        existingUser.setUsername("johndoe");

        when(valueOperations.get("otp:verified:" + testEmail)).thenReturn("true");
        when(valueOperations.get("registration:validate:" + testEmail)).thenReturn(testCitizenId + "|" + testPhoneNumber);
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(existingUser));

        // When & Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Username already exists");

        verify(keycloakClient, never()).createUser(anyString(), anyString(), anyString(), anyString());
    }

    // ===== login Tests =====

    @Test
    @DisplayName("login() should return token successfully")
    void testLogin_Success() {
        // Given
        LoginRequest request = new LoginRequest("johndoe", "password123");
        TokenResponse expectedToken = new TokenResponse(
                "access-token",
                "refresh-token",
                3600L,
                86400L,
                "id-token",
                "Bearer",
                "openid profile email"
        );

        when(keycloakClient.loginWithPassword("johndoe", "password123")).thenReturn(expectedToken);

        // When
        TokenResponse result = authService.login(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(keycloakClient).loginWithPassword("johndoe", "password123");
    }

    // ===== logout Tests =====

    @Test
    @DisplayName("logout() should call Keycloak logout")
    void testLogout_Success() {
        // Given
        LogoutRequest request = new LogoutRequest("refresh-token");

        // When
        authService.logout(request);

        // Then
        verify(keycloakClient).logout("refresh-token");
    }

    // ===== refresh Tests =====

    @Test
    @DisplayName("refresh() should return new token")
    void testRefresh_Success() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        TokenResponse expectedToken = new TokenResponse(
                "new-access-token",
                "new-refresh-token",
                3600L,
                86400L,
                "new-id-token",
                "Bearer",
                "openid profile email"
        );

        when(keycloakClient.refreshToken("refresh-token")).thenReturn(expectedToken);

        // When
        TokenResponse result = authService.refresh(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        verify(keycloakClient).refreshToken("refresh-token");
    }

    // ===== changePassword Tests =====

    @Test
    @DisplayName("changePassword() should change password successfully")
    void testChangePassword_Success() {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword", "newPassword");
        String userId = "user-123";
        String username = "johndoe";

        // When
        authService.changePassword(request, userId, username);

        // Then
        verify(keycloakClient).verifyPassword(username, "oldPassword");
        verify(keycloakClient).resetPassword(userId, "newPassword");
    }

    @Test
    @DisplayName("changePassword() should throw exception when old password incorrect")
    void testChangePassword_IncorrectOldPassword() {
        // Given
        ChangePasswordRequest request = new ChangePasswordRequest("wrongPassword", "newPassword");
        String userId = "user-123";
        String username = "johndoe";

        doThrow(new AppException(null, "Wrong password"))
                .when(keycloakClient).verifyPassword(username, "wrongPassword");

        // When & Then
        assertThatThrownBy(() -> authService.changePassword(request, userId, username))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Old password is incorrect");

        verify(keycloakClient).verifyPassword(username, "wrongPassword");
        verify(keycloakClient, never()).resetPassword(anyString(), anyString());
    }
}
