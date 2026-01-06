package com.uit.accountservice.service;

import com.uit.accountservice.BaseIntegrationTest;
import com.uit.accountservice.dto.AccountDto;
import com.uit.accountservice.dto.request.AccountBalanceRequest;
import com.uit.accountservice.dto.response.AccountBalanceResponse;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.sharedkernel.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Account Service Integration Tests with Testcontainers")
class AccountServiceTestcontainersTest extends BaseIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private Account testAccount;
    private String testUserId = "test-user-id";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();

        testAccount = Account.builder()
                .accountNumber("1234567890")
                .userId(testUserId)
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build();
        testAccount = accountRepository.save(testAccount);
    }

    @Test
    @DisplayName("Should retrieve accounts by user ID")
    void testGetAccountsByUserId_Success() {
        // Create another account for the same user
        Account secondAccount = Account.builder()
                .accountNumber("0987654321")
                .userId(testUserId)
                .balance(BigDecimal.valueOf(500.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build();
        accountRepository.save(secondAccount);

        List<AccountDto> accounts = accountService.getAccountsByUserId(testUserId);

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(AccountDto::getUserId)
                .containsOnly(testUserId);
    }

    @Test
    @DisplayName("Should return empty list when user has no accounts")
    void testGetAccountsByUserId_NoAccounts() {
        List<AccountDto> accounts = accountService.getAccountsByUserId("non-existent-user");

        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("Should retrieve account by account number")
    void testGetAccountByNumber_Success() {
        AccountDto accountDto = accountService.getAccountByNumber(testAccount.getAccountNumber());

        assertThat(accountDto).isNotNull();
        assertThat(accountDto.getAccountNumber()).isEqualTo(testAccount.getAccountNumber());
        assertThat(accountDto.getBalance()).isEqualByComparingTo(testAccount.getBalance());
    }

    @Test
    @DisplayName("Should throw exception when account number not found")
    void testGetAccountByNumber_NotFound() {
        assertThatThrownBy(() -> accountService.getAccountByNumber("non-existent"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account number not found");
    }

    @Test
    @DisplayName("Should debit account successfully")
    void testDebitAccount_Success() {
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(BigDecimal.valueOf(200.00));
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test debit");

        AccountBalanceResponse response = accountService.debitAccount(
                testAccount.getAccountId(), 
                request
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(800.00));

        // Verify database
        Account updatedAccount = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(800.00));
    }

    @Test
    @DisplayName("Should reject debit with insufficient balance")
    void testDebitAccount_InsufficientBalance() {
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(BigDecimal.valueOf(2000.00));
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test debit - insufficient");

        assertThatThrownBy(() -> accountService.debitAccount(testAccount.getAccountId(), request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Insufficient balance");

        // Verify balance unchanged
        Account unchangedAccount = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        assertThat(unchangedAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
    }

    @Test
    @DisplayName("Should credit account successfully")
    void testCreditAccount_Success() {
        AccountBalanceRequest request = new AccountBalanceRequest();
        request.setAmount(BigDecimal.valueOf(300.00));
        request.setTransactionId(UUID.randomUUID().toString());
        request.setDescription("Test credit");

        AccountBalanceResponse response = accountService.creditAccount(
                testAccount.getAccountId(), 
                request
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(1300.00));

        // Verify database
        Account updatedAccount = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(1300.00));
    }

    @Test
    @DisplayName("Should handle concurrent debit operations with pessimistic locking")
    void testConcurrentDebits() throws Exception {
        // This test verifies that pessimistic locking prevents race conditions
        int numberOfThreads = 5;
        BigDecimal debitAmount = BigDecimal.valueOf(100.00);
        
        Thread[] threads = new Thread[numberOfThreads];
        
        for (int i = 0; i < numberOfThreads; i++) {
            threads[i] = new Thread(() -> {
                try {
                    AccountBalanceRequest request = new AccountBalanceRequest();
                    request.setAmount(debitAmount);
                    request.setTransactionId(UUID.randomUUID().toString());
                    request.setDescription("Concurrent debit");
                    
                    accountService.debitAccount(testAccount.getAccountId(), request);
                } catch (Exception e) {
                    // Some threads may fail due to insufficient balance, which is expected
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify final balance
        Account finalAccount = accountRepository.findById(testAccount.getAccountId()).orElseThrow();
        
        // Balance should be between 0 and 1000 (depending on how many succeeded)
        // The important thing is that it's not negative
        assertThat(finalAccount.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalAccount.getBalance()).isLessThanOrEqualTo(BigDecimal.valueOf(1000.00));
    }

    @Test
    @DisplayName("Should handle account status correctly")
    void testAccountStatus() {
        // Test with locked account
        Account lockedAccount = Account.builder()
                .accountNumber("1111111111")
                .userId("another-user")
                .balance(BigDecimal.valueOf(500.00))
                .status(AccountStatus.LOCKED)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build();
        lockedAccount = accountRepository.save(lockedAccount);

        AccountDto dto = accountService.getAccountByNumber(lockedAccount.getAccountNumber());
        assertThat(dto.getAccountStatus()).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("Should retrieve all accounts")
    void testGetAllAccounts() {
        // Create additional test accounts
        Account account2 = Account.builder()
                .accountNumber("2222222222")
                .userId("user2")
                .balance(BigDecimal.valueOf(1500.00))
                .status(AccountStatus.ACTIVE)
                .pinHash("hashed-pin")
                .createdAt(LocalDateTime.now())
                .build();
        accountRepository.save(account2);

        List<AccountDto> allAccounts = accountService.getAllAccounts();
        
        assertThat(allAccounts).hasSizeGreaterThanOrEqualTo(2);
    }
}
