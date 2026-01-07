package com.uit.userservice.service;

import com.uit.sharedkernel.audit.AuditEventPublisher;
import com.uit.sharedkernel.exception.AppException;
import com.uit.userservice.client.AccountClient;
import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.UserResponse;
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
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserServiceImpl
 * Tests user profile management and CRUD operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakClient keycloakClient;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private String testUserId;
    private Jwt testJwt;

    @BeforeEach
    void setUp() {
        testUserId = "user-123";

        testUser = new User();
        testUser.setId(testUserId);
        testUser.setUsername("johndoe");
        testUser.setEmail("john@example.com");
        testUser.setFullName("John Doe");
        testUser.setCitizenId("123456789");
        testUser.setDob(LocalDate.of(1990, 1, 1));
        testUser.setPhoneNumber("0123456789");
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setIsFaceRegistered(false);

        // Mock JWT
        testJwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", testUserId)
                .claim("preferred_username", "johndoe")
                .claim("email", "john@example.com")
                .claim("name", "John Doe")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    // ===== getCurrentUser Tests =====

    @Test
    @DisplayName("getCurrentUser() should return existing user")
    void testGetCurrentUser_ExistingUser() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getCurrentUser(testJwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testUserId);
        assertThat(result.username()).isEqualTo("johndoe");
        assertThat(result.email()).isEqualTo("john@example.com");
        assertThat(result.fullName()).isEqualTo("John Doe");
        verify(userRepository).findById(testUserId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getCurrentUser() should create user from token if not exists")
    void testGetCurrentUser_CreateFromToken() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        UserResponse result = userService.getCurrentUser(testJwt);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testUserId);
        verify(userRepository).findById(testUserId);
        verify(userRepository).save(any(User.class));
        verify(auditEventPublisher).publishAuditEvent(any());
    }

    // ===== updateCurrentUser Tests =====

    @Test
    @DisplayName("updateCurrentUser() should update user successfully")
    void testUpdateCurrentUser_Success() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("John Doe Updated")
                .email("john.doe@example.com")
                .dob(LocalDate.of(1990, 5, 15))
                .phoneNumber("0987654321")
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.updateCurrentUser(testJwt, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(testUser.getFullName()).isEqualTo("John Doe Updated");
        assertThat(testUser.getDob()).isEqualTo(LocalDate.of(1990, 5, 15));
        assertThat(testUser.getPhoneNumber()).isEqualTo("0987654321");
        verify(userRepository).findById(testUserId);
        verify(auditEventPublisher).publishAuditEvent(any());
    }

    @Test
    @DisplayName("updateCurrentUser() should throw exception when user not found")
    void testUpdateCurrentUser_UserNotFound() {
        // Given
        UpdateUserRequest request = UpdateUserRequest.builder()
                .fullName("John Updated")
                .email("john.doe@example.com")
                .dob(LocalDate.of(1990, 5, 15))
                .phoneNumber("0987654321")
                .build();

        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateCurrentUser(testJwt, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("User not found");

        verify(auditEventPublisher, never()).publishAuditEvent(any());
    }

    // ===== getUserById Tests =====

    @Test
    @DisplayName("getUserById() should return user when exists")
    void testGetUserById_Success() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        UserResponse result = userService.getUserById(testUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(testUserId);
        assertThat(result.username()).isEqualTo("johndoe");
        assertThat(result.email()).isEqualTo("john@example.com");
        verify(userRepository).findById(testUserId);
    }

    @Test
    @DisplayName("getUserById() should throw exception when user not found")
    void testGetUserById_NotFound() {
        // Given
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserById("nonexistent"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("User not found");

        verify(userRepository).findById("nonexistent");
    }

    // ===== lockUser Tests =====

    @Test
    @DisplayName("lockUser() should lock user in Keycloak")
    void testLockUser_Success() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        userService.lockUser(testUserId);

        // Then
        verify(userRepository).findById(testUserId);
        verify(keycloakClient).updateUserStatus(testUserId, false);
    }

    @Test
    @DisplayName("lockUser() should throw exception when user not found")
    void testLockUser_UserNotFound() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.lockUser(testUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("User not found");

        verify(keycloakClient, never()).updateUserStatus(any(), anyBoolean());
    }

    // ===== unlockUser Tests =====

    @Test
    @DisplayName("unlockUser() should unlock user in Keycloak")
    void testUnlockUser_Success() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        userService.unlockUser(testUserId);

        // Then
        verify(userRepository).findById(testUserId);
        verify(keycloakClient).updateUserStatus(testUserId, true);
    }

    @Test
    @DisplayName("unlockUser() should throw exception when user not found")
    void testUnlockUser_UserNotFound() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.unlockUser(testUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("User not found");

        verify(keycloakClient, never()).updateUserStatus(any(), anyBoolean());
    }

    // ===== getUserDetailForAdmin Tests =====

    @Test
    @DisplayName("getUserDetailForAdmin() should return user details with Keycloak status")
    void testGetUserDetailForAdmin_Success() {
        // Given
        Map<String, Object> keycloakInfo = Map.of("enabled", true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(keycloakClient.getUserFromKeycloak(testUserId)).thenReturn(keycloakInfo);

        // When
        var result = userService.getUserDetailForAdmin(testUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("user-123");
        assertThat(result.username()).isEqualTo("johndoe");
        assertThat(result.isEnable()).isTrue();
        verify(userRepository).findById(testUserId);
        verify(keycloakClient).getUserFromKeycloak(testUserId);
    }

    @Test
    @DisplayName("getUserDetailForAdmin() should handle disabled user")
    void testGetUserDetailForAdmin_DisabledUser() {
        // Given
        Map<String, Object> keycloakInfo = Map.of("enabled", false);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(keycloakClient.getUserFromKeycloak(testUserId)).thenReturn(keycloakInfo);

        // When
        var result = userService.getUserDetailForAdmin(testUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isEnable()).isFalse();
        verify(keycloakClient).getUserFromKeycloak(testUserId);
    }

    @Test
    @DisplayName("getUserDetailForAdmin() should throw exception when user not found")
    void testGetUserDetailForAdmin_NotFound() {
        // Given
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.getUserDetailForAdmin("nonexistent"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("User not found");

        verify(keycloakClient, never()).getUserFromKeycloak(any());
    }
}
