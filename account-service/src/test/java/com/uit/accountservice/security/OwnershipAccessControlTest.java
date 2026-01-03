package com.uit.accountservice.security;

import com.uit.accountservice.AbstractIntegrationTest;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.accountservice.riskengine.RiskEngineService;
import com.uit.accountservice.riskengine.dto.RiskAssessmentRequest;
import com.uit.accountservice.riskengine.dto.RiskAssessmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security Integration Tests for OWASP A01:2021 Broken Access Control
 * 
 * These tests verify that ownership-based access control is properly enforced:
 * - Users can ONLY access accounts they own
 * - Users can ONLY initiate transfers from accounts they own
 * - Unauthorized access returns 403 Forbidden
 */
@AutoConfigureMockMvc
@DisplayName("Ownership Access Control Security Tests")
class OwnershipAccessControlTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private RiskEngineService riskEngineService;

    @MockBean
    private WebClient.Builder webClientBuilder;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        // Mock JWT decoder to parse token and extract claims from payload
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            // Parse JWT token (header.payload.signature) to extract subject and roles
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                try {
                    String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> claims = mapper.readValue(payload, Map.class);
                    String sub = (String) claims.get("sub");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) realmAccess.get("roles");
                    
                    return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .claim("sub", sub)
                            .claim("realm_access", Map.of("roles", roles))
                            .build();
                } catch (Exception e) {
                    // Fallback for malformed tokens
                    return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                            .header("alg", "none")
                            .claim("sub", "test-user")
                            .claim("realm_access", Map.of("roles", List.of("user")))
                            .build();
                }
            }
            // Fallback for invalid tokens
            return org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .claim("realm_access", Map.of("roles", List.of("user")))
                    .build();
        });
        
        // Mock dependencies for happy path
        RiskAssessmentResponse lowRisk = new RiskAssessmentResponse();
        lowRisk.setRiskLevel("LOW");
        lowRisk.setChallengeType("NONE");
        when(riskEngineService.assessRisk(any(RiskAssessmentRequest.class))).thenReturn(lowRisk);

        WebClient webClient = mock(WebClient.class);
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());

        // Mock Redis
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        // Clean slate
        accountRepository.deleteAllInBatch();

        // Alice's account - let Hibernate generate ID
        aliceAccount = accountRepository.saveAndFlush(Account.builder()
                .accountNumber("ACC-ALICE-001")
                .userId("alice-user-id")
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Bob's account - let Hibernate generate ID
        bobAccount = accountRepository.saveAndFlush(Account.builder()
                .accountNumber("ACC-BOB-002")
                .userId("bob-user-id")
                .balance(BigDecimal.valueOf(2000.00))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("User can access their own account")
    void testUserCanAccessOwnAccount() throws Exception {
        String aliceToken = createMockJwtToken("alice-user-id", "user");
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId())
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("X-Userinfo", createUserInfoHeader("alice-user-id", "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(aliceAccount.getAccountId()))
                .andExpect(jsonPath("$.data.userId").value("alice-user-id"))
                .andExpect(jsonPath("$.data.balance").value(1000.00));
    }

    @Test
    @DisplayName("User CANNOT access another user's account")
    void testUserCannotAccessOtherUserAccount() throws Exception {
        String aliceToken = createMockJwtToken("alice-user-id", "user");
        mockMvc.perform(get("/accounts/{accountId}", bobAccount.getAccountId())
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("X-Userinfo", createUserInfoHeader("alice-user-id", "user")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated user cannot access any account")
    void testUnauthenticatedUserCannotAccessAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("User without 'user' role cannot access accounts")
    void testUserWithoutRoleCannotAccessAccount() throws Exception {
        String guestToken = createMockJwtToken("alice-user-id", "guest");
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId())
                        .header("Authorization", "Bearer " + guestToken)
                        .header("X-Userinfo", createUserInfoHeader("alice-user-id", "guest")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can access admin dashboard")
    void testAdminCanAccessDashboard() throws Exception {
        String adminToken = createMockJwtToken("admin-user-id", "admin");
        mockMvc.perform(get("/accounts/dashboard")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Userinfo", createUserInfoHeader("admin-user-id", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Admin Dashboard"));
    }

    @Test
    @DisplayName("Regular user CANNOT access admin dashboard")
    void testUserCannotAccessAdminDashboard() throws Exception {
        String userToken = createMockJwtToken("test-user", "user");
        mockMvc.perform(get("/accounts/dashboard")
                        .header("Authorization", "Bearer " + userToken)
                        .header("X-Userinfo", createUserInfoHeader("alice-user-id", "user")))
                .andExpect(status().isForbidden());
    }

    /**
     * Helper method to create X-Userinfo header value for testing
     */
    private String createUserInfoHeader(String userId, String role) {
        // UserInfoAuthentication expects realm_access as Map with "roles" key
        String json = String.format("{\"sub\":\"%s\",\"realm_access\":{\"roles\":[\"%s\"]}}", userId, role);
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    /**
     * Helper method to create a properly formatted JWT token for testing
     * ParseUserInfoFilter requires JWT format: header.payload.signature
     */
    private String createMockJwtToken(String userId, String role) {
        // Header: {"alg":"none","typ":"JWT"}
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        
        // Payload with user info - UserInfoAuthentication expects realm_access as Map with "roles" key
        String payload = String.format("{\"sub\":\"%s\",\"realm_access\":{\"roles\":[\"%s\"]}}", userId, role);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        
        // No signature for test token
        return header + "." + encodedPayload + ".";
    }
}
