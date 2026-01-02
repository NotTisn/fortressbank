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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security Integration Tests for OWASP A01:2021 Broken Access Control
 * 
 * These tests verify that ownership-based access control is properly enforced:
 * - Users can ONLY access accounts they own
 * - Admin-only endpoints require admin role
 * - Unauthorized access returns appropriate error codes
 * 
 * Uses Spring Security Test's jwt() post processor for proper OAuth2 testing.
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
                .userId("alice-user-id")
                .accountNumber("1234567890")
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Bob's account - let Hibernate generate ID
        bobAccount = accountRepository.saveAndFlush(Account.builder()
                .userId("bob-user-id")
                .accountNumber("0987654321")
                .balance(BigDecimal.valueOf(2000.00))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("✅ User can access their own account")
    void testUserCanAccessOwnAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId())
                        .with(jwt().jwt(jwt -> jwt
                                .subject("alice-user-id")
                                .claim("realm_access", Map.of("roles", List.of("user"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(aliceAccount.getAccountId()))
                .andExpect(jsonPath("$.data.userId").value("alice-user-id"))
                .andExpect(jsonPath("$.data.balance").value(1000.00));
    }

    @Test
    @DisplayName("🚫 User CANNOT access another user's account")
    void testUserCannotAccessOtherUserAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", bobAccount.getAccountId())
                        .with(jwt().jwt(jwt -> jwt
                                .subject("alice-user-id")
                                .claim("realm_access", Map.of("roles", List.of("user"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("🚫 Unauthenticated user cannot access any account")
    void testUnauthenticatedUserCannotAccessAccount() throws Exception {
        mockMvc.perform(get("/accounts/{accountId}", aliceAccount.getAccountId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("✅ Admin can access admin dashboard")
    void testAdminCanAccessDashboard() throws Exception {
        mockMvc.perform(get("/accounts/dashboard")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("admin-user-id")
                                .claim("realm_access", Map.of("roles", List.of("admin"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Admin Dashboard"));
    }

    @Test
    @DisplayName("🚫 Regular user CANNOT access admin dashboard")
    void testUserCannotAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/accounts/dashboard")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("alice-user-id")
                                .claim("realm_access", Map.of("roles", List.of("user"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}
