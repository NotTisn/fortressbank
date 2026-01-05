package com.uit.accountservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.accountservice.BaseIntegrationTest;
import com.uit.accountservice.dto.request.AccountBalanceRequest;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;
import com.uit.accountservice.repository.AccountRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@DisplayName("Account Controller Integration Tests")
class AccountControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Account testAccount;
    private Account anotherAccount;
    private Jwt mockJwt;
    private String testUserId = "test-user-id";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        // Create test accounts
        testAccount = Account.builder()
                .accountNumber("1234567890")
                .userId(testUserId)
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build();
        testAccount = accountRepository.save(testAccount);

        anotherAccount = Account.builder()
                .accountNumber("0987654321")
                .userId(testUserId)
                .balance(BigDecimal.valueOf(500.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build();
        anotherAccount = accountRepository.save(anotherAccount);

        // Mock JWT token
        mockJwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", testUserId)
                .claim("preferred_username", "testuser")
                .claim("email", "test@example.com")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("user")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(anyString())).thenReturn(mockJwt);
    }

    @Test
    @DisplayName("GET /accounts/ - Should return health check")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/accounts/")
                        .with(jwt().jwt(mockJwt))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.status").value("Account Service is running"));
    }

    @Test
    @DisplayName("GET /accounts/my-accounts - Should return 403 when not authenticated")
    void testGetMyAccounts_Unauthorized() throws Exception {
        mockMvc.perform(get("/accounts/my-accounts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /accounts/internal/{accountId}/debit - Should debit account successfully")
    void testDebitAccount_Success() throws Exception {
        String accountId = testAccount.getAccountId();
        BigDecimal debitAmount = BigDecimal.valueOf(100.00);
        
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(debitAmount);
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test debit");

        mockMvc.perform(post("/accounts/internal/{accountId}/debit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newBalance").exists());

        // Verify balance updated
        Account updatedAccount = accountRepository.findById(accountId).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(900.00));
    }

    @Test
    @DisplayName("POST /accounts/internal/{accountId}/debit - Should reject insufficient balance")
    void testDebitAccount_InsufficientBalance() throws Exception {
        String accountId = testAccount.getAccountId();
        BigDecimal debitAmount = BigDecimal.valueOf(2000.00); // More than balance
        
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(debitAmount);
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test debit - insufficient");

        mockMvc.perform(post("/accounts/internal/{accountId}/debit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Verify balance unchanged
        Account unchangedAccount = accountRepository.findById(accountId).orElseThrow();
        assertThat(unchangedAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
    }

    @Test
    @DisplayName("POST /accounts/internal/{accountId}/credit - Should credit account successfully")
    void testCreditAccount_Success() throws Exception {
        String accountId = testAccount.getAccountId();
        BigDecimal creditAmount = BigDecimal.valueOf(200.00);
        
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(creditAmount);
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test credit");

        mockMvc.perform(post("/accounts/internal/{accountId}/credit", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newBalance").exists());

        // Verify balance updated
        Account updatedAccount = accountRepository.findById(accountId).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1200.00));
    }

    @Test
    @DisplayName("POST /accounts/internal/{accountId}/debit - Should handle account not found")
    void testDebitAccount_NotFound() throws Exception {
        String nonExistentAccountId = UUID.randomUUID().toString();
        
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test debit - not found");

        mockMvc.perform(post("/accounts/internal/{accountId}/debit", nonExistentAccountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
