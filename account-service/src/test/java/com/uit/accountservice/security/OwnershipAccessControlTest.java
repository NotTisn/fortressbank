package com.uit.accountservice.security;

import com.uit.accountservice.BaseIntegrationTest;
import com.uit.accountservice.entity.Account;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class OwnershipAccessControlTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private RiskEngineService riskEngineService;

    @MockBean
    private WebClient.Builder webClientBuilder;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Account aliceAccount;
    private Account bobAccount;
    private Jwt aliceJwt;
    private Jwt bobJwt;
    private Jwt adminJwt;
    private Jwt guestJwt;

    @BeforeEach
    void setUp() {
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

        // Clean slate
        accountRepository.deleteAllInBatch();

        // Alice's account
        aliceAccount = accountRepository.saveAndFlush(Account.builder()
                .accountNumber("1111111111")
                .userId("alice-user-id")
                .balance(BigDecimal.valueOf(1000.00))
                .status(com.uit.accountservice.entity.enums.AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build());

        // Bob's account
        bobAccount = accountRepository.saveAndFlush(Account.builder()
                .accountNumber("2222222222")
                .userId("bob-user-id")
                .balance(BigDecimal.valueOf(2000.00))
                .status(com.uit.accountservice.entity.enums.AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build());

        // Mock JWTs for different users
        aliceJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "alice-user-id")
                .claim("preferred_username", "alice")
                .claim("email", "alice@example.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("user")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        bobJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "bob-user-id")
                .claim("preferred_username", "bob")
                .claim("email", "bob@example.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("user")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        adminJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "admin-user-id")
                .claim("preferred_username", "admin")
                .claim("email", "admin@example.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("admin")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        guestJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "alice-user-id")
                .claim("preferred_username", "guest")
                .claim("email", "guest@example.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("guest")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(anyString())).thenReturn(aliceJwt);
    }

    @Test
    @DisplayName("✅ User can access their own account")
    void testUserCanAccessOwnAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId())
                        .with(jwt().jwt(aliceJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(aliceAccount.getAccountId()))
                .andExpect(jsonPath("$.data.userId").value("alice-user-id"))
                .andExpect(jsonPath("$.data.balance").value(1000.00));
    }

    @Test
    @DisplayName("🚫 User CANNOT access another user's account")
    void testUserCannotAccessOtherUserAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", bobAccount.getAccountId())
                        .with(jwt().jwt(aliceJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("🚫 Unauthenticated user cannot access any account")
    void testUnauthenticatedUserCannotAccessAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("✅ User can initiate transfer from their own account")
    void testUserCanTransferFromOwnAccount() throws Exception {
        String transferJson = String.format("""
                {
                    "fromAccountId": "%s",
                    "toAccountId": "%s",
                    "amount": 100.00,
                    "description": "Test transfer"
                }
                """, aliceAccount.getAccountId(), bobAccount.getAccountId());

        mockMvc.perform(post("/accounts/transfers")
                        .with(jwt().jwt(aliceJwt))
                        .contentType("application/json")
                        .content(transferJson))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("🚫 User CANNOT initiate transfer from another user's account")
    void testUserCannotTransferFromOtherUserAccount() throws Exception {
        String transferJson = String.format("""
                {
                    "fromAccountId": "%s",
                    "toAccountId": "%s",
                    "amount": 100.00,
                    "description": "Unauthorized transfer attempt"
                }
                """, bobAccount.getAccountId(), aliceAccount.getAccountId());

        mockMvc.perform(post("/accounts/transfers")
                        .with(jwt().jwt(aliceJwt))
                        .contentType("application/json")
                        .content(transferJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("🚫 User without 'user' role cannot access accounts")
    void testUserWithoutRoleCannotAccessAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId())
                        .with(jwt().jwt(guestJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("✅ Admin can access admin dashboard")
    void testAdminCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/accounts/dashboard")
                        .with(jwt().jwt(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Admin Dashboard"));
    }

    @Test
    @DisplayName("🚫 Regular user CANNOT access admin dashboard")
    void testUserCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/accounts/dashboard")
                        .with(jwt().jwt(aliceJwt)))
                .andExpect(status().isForbidden());
    }
}
