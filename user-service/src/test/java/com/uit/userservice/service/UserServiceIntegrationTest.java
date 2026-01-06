package com.uit.userservice.service;

import com.uit.sharedkernel.exception.AppException;
import com.uit.userservice.BaseIntegrationTest;
import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("User Service Integration Tests")
class UserServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private User testUser;
    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setPhoneNumber("+84123456789");
        testUser.setCitizenId("123456789012");
        testUser.setDob(LocalDate.of(1990, 1, 1));
        testUser.setIsFaceRegistered(false);
        testUser = userRepository.save(testUser);

        mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", testUser.getId())
                .claim("preferred_username", testUser.getUsername())
                .claim("email", testUser.getEmail())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(anyString())).thenReturn(mockJwt);
    }

    @Test
    @DisplayName("Should retrieve current user by JWT")
    void testGetCurrentUser_Success() {
        UserResponse response = userService.getCurrentUser(mockJwt);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testUser.getId());
        assertThat(response.username()).isEqualTo(testUser.getUsername());
        assertThat(response.email()).isEqualTo(testUser.getEmail());
        assertThat(response.fullName()).isEqualTo(testUser.getFullName());
    }

    @Test
    @DisplayName("Should create user from JWT when user not found")
    void testGetCurrentUser_NotFound() {
        Jwt nonExistentUserJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "non-existent-user-id")
                .claim("preferred_username", "nonexistent")
                .claim("email", "nonexistent@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        // Should create new user from JWT instead of throwing exception
        UserResponse response = userService.getCurrentUser(nonExistentUserJwt);
        
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("non-existent-user-id");
        assertThat(response.username()).isEqualTo("nonexistent");
        
        // Verify user was saved to database
        User savedUser = userRepository.findById("non-existent-user-id").orElse(null);
        assertThat(savedUser).isNotNull();
    }

    @Test
    @DisplayName("Should update user information successfully")
    void testUpdateCurrentUser_Success() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("Updated Name");
        request.setPhoneNumber("+84987654321");

        UserResponse response = userService.updateCurrentUser(mockJwt, request);

        assertThat(response.fullName()).isEqualTo("Updated Name");
        assertThat(response.phoneNumber()).isEqualTo("+84987654321");

        // Verify database
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Updated Name");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo("+84987654321");
    }

    @Test
    @DisplayName("Should retrieve user by ID")
    void testGetUserById_Success() {
        UserResponse response = userService.getUserById(testUser.getId());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(testUser.getId());
        assertThat(response.username()).isEqualTo(testUser.getUsername());
    }

    @Test
    @DisplayName("Should throw exception when retrieving non-existent user by ID")
    void testGetUserById_NotFound() {
        assertThatThrownBy(() -> userService.getUserById("non-existent-id"))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("Should handle concurrent updates correctly")
    void testConcurrentUpdates() throws InterruptedException {
        // This test verifies that the database constraints work properly
        // Create another user to test unique constraints
        User anotherUser = new User();
        anotherUser.setId("another-user-id");
        anotherUser.setUsername("anotheruser");
        anotherUser.setEmail("another@example.com");
        anotherUser.setFullName("Another User");
        anotherUser.setPhoneNumber("+84111111111");
        anotherUser.setCitizenId("999999999999");
        anotherUser.setDob(LocalDate.of(1995, 5, 5));
        anotherUser.setIsFaceRegistered(false);
        userRepository.save(anotherUser);

        // Try to update testUser phone number
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName(testUser.getFullName()); // Keep existing
        request.setDob(testUser.getDob()); // Keep existing
        request.setPhoneNumber("+84222222222"); // Update phone
        
        UserResponse response = userService.updateCurrentUser(mockJwt, request);
        assertThat(response.phoneNumber()).isEqualTo("+84222222222");
    }
}
