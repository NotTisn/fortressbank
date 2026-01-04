package com.uit.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.sharedkernel.api.ApiResponse;
import com.uit.userservice.BaseIntegrationTest;
import com.uit.userservice.dto.request.UpdateUserRequest;
import com.uit.userservice.dto.response.UserResponse;
import com.uit.userservice.entity.User;
import com.uit.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("User Controller Integration Tests")
class UserControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private User testUser;
    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create test user
        testUser = new User();
        testUser.setId("test-user-id");
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
        testUser.setPhoneNumber("0123456789");
        testUser.setCitizenId("123456789012");
        testUser.setDob(LocalDate.of(1990, 1, 1));
        testUser.setIsFaceRegistered(false);
        testUser = userRepository.save(testUser);

        // Mock JWT token
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
    @DisplayName("GET /users/me - Should return current user information")
    void testGetMe_Success() throws Exception {
        mockMvc.perform(get("/users/me")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.id").value(testUser.getId()))
                .andExpect(jsonPath("$.data.username").value(testUser.getUsername()))
                .andExpect(jsonPath("$.data.email").value(testUser.getEmail()))
                .andExpect(jsonPath("$.data.fullName").value(testUser.getFullName()))
                .andExpect(jsonPath("$.data.phoneNumber").value(testUser.getPhoneNumber()));
    }

    @Test
    @DisplayName("GET /users/me - Should return 401 when not authenticated")
    void testGetMe_Unauthorized() throws Exception {
        mockMvc.perform(get("/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /users/me - Should update user information successfully")
    void testUpdateMe_Success() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("Updated Name");
        request.setDob(testUser.getDob()); // Keep existing dob
        request.setPhoneNumber("0987654321");

        mockMvc.perform(patch("/users/me")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0987654321"));

        // Verify database update
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Updated Name");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo("0987654321");
    }

    @Test
    @DisplayName("PATCH /users/me - Should reject invalid phone number")
    void testUpdateMe_InvalidPhoneNumber() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPhoneNumber("invalid-phone");

        mockMvc.perform(patch("/users/me")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify database not updated
        User unchangedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(unchangedUser.getPhoneNumber()).isEqualTo(testUser.getPhoneNumber());
    }

    @Test
    @DisplayName("PATCH /users/me - Should handle partial updates")
    void testUpdateMe_PartialUpdate() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFullName("Only Name Updated");
        request.setDob(testUser.getDob()); // Keep existing
        request.setPhoneNumber(testUser.getPhoneNumber()); // Keep existing

        String originalPhoneNumber = testUser.getPhoneNumber();

        mockMvc.perform(patch("/users/me")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.fullName").value("Only Name Updated"));

        // Verify only fullName is updated, phoneNumber remains unchanged
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getFullName()).isEqualTo("Only Name Updated");
        assertThat(updatedUser.getPhoneNumber()).isEqualTo(originalPhoneNumber);
    }
}
