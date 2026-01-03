package com.uit.accountservice.service;

import com.uit.accountservice.client.UserClient;
import com.uit.accountservice.dto.AccountDto;
import com.uit.accountservice.dto.request.AccountBalanceRequest;
import com.uit.accountservice.dto.request.InternalTransferRequest;
import com.uit.accountservice.dto.response.AccountBalanceResponse;
import com.uit.accountservice.dto.response.InternalTransferResponse;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;
import com.uit.accountservice.mapper.AccountMapper;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.accountservice.riskengine.RiskEngineService;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountService
 * Tests account operations: debit, credit, transfer, balance inquiries
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private RiskEngineService riskEngineService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private TransferAuditService auditService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserClient userClient;

    @Mock
    private CardService cardService;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;
    private AccountDto testAccountDto;
    private String testAccountId;

    @BeforeEach
    void setUp() {
        testAccountId = "account-123";

        testAccount = Account.builder()
                .accountId(testAccountId)
                .accountNumber("1234567890")
                .userId("user-123")
                .balance(BigDecimal.valueOf(1000.00))
                .pinHash("hashed-pin")
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        testAccountDto = AccountDto.builder()
                .accountId(testAccountId)
                .accountNumber("1234567890")
                .userId("user-123")
                .balance(BigDecimal.valueOf(1000.00))
                .accountStatus(AccountStatus.ACTIVE.name())
                .build();
    }

    // ===== debitAccount Tests =====

    @Test
    @DisplayName("debitAccount() should deduct amount successfully")
    void testDebitAccount_Success() {
        // Given
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .amount(BigDecimal.valueOf(100.00))
                .transactionId("tx-123")
                .description("Test debit")
                .build();

        when(accountRepository.findByIdWithLock(testAccountId)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // When
        AccountBalanceResponse response = accountService.debitAccount(testAccountId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOldBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(response.getNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(900.00));
        assertThat(response.getTransactionId()).isEqualTo("tx-123");

        verify(accountRepository).findByIdWithLock(testAccountId);
        verify(accountRepository).save(argThat(account -> 
            account.getBalance().compareTo(BigDecimal.valueOf(900.00)) == 0
        ));
        verify(auditEventPublisher).publishAuditEvent(any());
    }

    @Test
    @DisplayName("debitAccount() should throw exception when insufficient balance")
    void testDebitAccount_InsufficientBalance() {
        // Given
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .amount(BigDecimal.valueOf(1500.00))
                .transactionId("tx-456")
                .description("Test debit")
                .build();

        when(accountRepository.findByIdWithLock(testAccountId)).thenReturn(Optional.of(testAccount));

        // When & Then
        assertThatThrownBy(() -> accountService.debitAccount(testAccountId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Insufficient balance")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("debitAccount() should throw exception when account not found")
    void testDebitAccount_AccountNotFound() {
        // Given
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .amount(BigDecimal.valueOf(100.00))
                .transactionId("tx-789")
                .description("Test debit")
                .build();

        when(accountRepository.findByIdWithLock(testAccountId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.debitAccount(testAccountId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account not found")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(accountRepository, never()).save(any());
    }

    // ===== creditAccount Tests =====

    @Test
    @DisplayName("creditAccount() should add amount successfully")
    void testCreditAccount_Success() {
        // Given
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .amount(BigDecimal.valueOf(500.00))
                .transactionId("tx-credit-123")
                .description("Test credit")
                .build();

        when(accountRepository.findByIdWithLock(testAccountId)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // When
        AccountBalanceResponse response = accountService.creditAccount(testAccountId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOldBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(response.getNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
        assertThat(response.getTransactionId()).isEqualTo("tx-credit-123");

        verify(accountRepository).findByIdWithLock(testAccountId);
        verify(accountRepository).save(argThat(account -> 
            account.getBalance().compareTo(BigDecimal.valueOf(1500.00)) == 0
        ));
        verify(auditEventPublisher).publishAuditEvent(any());
    }

    @Test
    @DisplayName("creditAccount() should throw exception when account not found")
    void testCreditAccount_AccountNotFound() {
        // Given
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .amount(BigDecimal.valueOf(500.00))
                .transactionId("tx-credit-456")
                .description("Test credit")
                .build();

        when(accountRepository.findByIdWithLock(testAccountId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.creditAccount(testAccountId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account not found")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(accountRepository, never()).save(any());
    }

    // ===== executeInternalTransfer Tests =====

    @Test
    @DisplayName("executeInternalTransfer() should transfer amount successfully between accounts")
    void testExecuteInternalTransfer_Success() {
        // Given
        Account senderAccount = Account.builder()
                .accountId("account-sender")
                .accountNumber("1111111111")
                .userId("user-sender")
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .build();

        Account receiverAccount = Account.builder()
                .accountId("account-receiver")
                .accountNumber("2222222222")
                .userId("user-receiver")
                .balance(BigDecimal.valueOf(500.00))
                .status(AccountStatus.ACTIVE)
                .build();

        InternalTransferRequest request = InternalTransferRequest.builder()
                .senderAccountId("account-sender")
                .receiverAccountId("account-receiver")
                .amount(BigDecimal.valueOf(200.00))
                .transactionId("tx-transfer-123")
                .description("Test transfer")
                .build();

        when(accountRepository.findByIdInWithLock(anyList())).thenReturn(Arrays.asList(senderAccount, receiverAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        InternalTransferResponse response = accountService.executeInternalTransfer(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo("tx-transfer-123");
        assertThat(response.getSenderAccountOldBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(response.getSenderAccountNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(800.00));
        assertThat(response.getReceiverAccountOldBalance()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(response.getReceiverAccountNewBalance()).isEqualByComparingTo(BigDecimal.valueOf(700.00));

        verify(accountRepository).findByIdInWithLock(anyList());
        verify(accountRepository, times(2)).save(any(Account.class));
        verify(auditEventPublisher, times(2)).publishAuditEvent(any());
    }

    @Test
    @DisplayName("executeInternalTransfer() should throw exception when sender has insufficient balance")
    void testExecuteInternalTransfer_InsufficientBalance() {
        // Given
        Account senderAccount = Account.builder()
                .accountId("account-sender")
                .accountNumber("1111111111")
                .userId("user-sender")
                .balance(BigDecimal.valueOf(100.00))
                .status(AccountStatus.ACTIVE)
                .build();

        Account receiverAccount = Account.builder()
                .accountId("account-receiver")
                .accountNumber("2222222222")
                .userId("user-receiver")
                .balance(BigDecimal.valueOf(500.00))
                .status(AccountStatus.ACTIVE)
                .build();

        InternalTransferRequest request = InternalTransferRequest.builder()
                .senderAccountId("account-sender")
                .receiverAccountId("account-receiver")
                .amount(BigDecimal.valueOf(200.00))
                .transactionId("tx-transfer-456")
                .description("Test transfer")
                .build();

        when(accountRepository.findByIdInWithLock(anyList())).thenReturn(Arrays.asList(senderAccount, receiverAccount));

        // When & Then
        assertThatThrownBy(() -> accountService.executeInternalTransfer(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Insufficient balance")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("executeInternalTransfer() should throw exception when one account not found")
    void testExecuteInternalTransfer_AccountNotFound() {
        // Given
        Account senderAccount = Account.builder()
                .accountId("account-sender")
                .accountNumber("1111111111")
                .userId("user-sender")
                .balance(BigDecimal.valueOf(1000.00))
                .status(AccountStatus.ACTIVE)
                .build();

        InternalTransferRequest request = InternalTransferRequest.builder()
                .senderAccountId("account-sender")
                .receiverAccountId("account-nonexistent")
                .amount(BigDecimal.valueOf(200.00))
                .transactionId("tx-transfer-789")
                .description("Test transfer")
                .build();

        // Only one account found instead of two
        when(accountRepository.findByIdInWithLock(anyList())).thenReturn(Arrays.asList(senderAccount));

        // When & Then
        assertThatThrownBy(() -> accountService.executeInternalTransfer(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("One or both accounts not found")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);

        verify(accountRepository, never()).save(any());
    }

    // ===== getAccountsByUserId Tests =====

    @Test
    @DisplayName("getAccountsByUserId() should return all accounts for user")
    void testGetAccountsByUserId() {
        // Given
        String userId = "user-123";
        Account account1 = Account.builder()
                .accountId("account-1")
                .userId(userId)
                .balance(BigDecimal.valueOf(1000))
                .build();

        Account account2 = Account.builder()
                .accountId("account-2")
                .userId(userId)
                .balance(BigDecimal.valueOf(2000))
                .build();

        when(accountRepository.findByUserId(userId)).thenReturn(Arrays.asList(account1, account2));
        when(accountMapper.toDto(any(Account.class))).thenReturn(testAccountDto);

        // When
        List<AccountDto> accounts = accountService.getAccountsByUserId(userId);

        // Then
        assertThat(accounts).hasSize(2);
        verify(accountRepository).findByUserId(userId);
        verify(accountMapper, times(2)).toDto(any(Account.class));
    }

    // ===== getAccountByNumber Tests =====

    @Test
    @DisplayName("getAccountByNumber() should return account when found")
    void testGetAccountByNumber_Success() {
        // Given
        String accountNumber = "1234567890";
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(testAccount));
        when(accountMapper.toDto(testAccount)).thenReturn(testAccountDto);

        // When
        AccountDto result = accountService.getAccountByNumber(accountNumber);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo(accountNumber);
        verify(accountRepository).findByAccountNumber(accountNumber);
    }

    @Test
    @DisplayName("getAccountByNumber() should throw exception when account not found")
    void testGetAccountByNumber_NotFound() {
        // Given
        String accountNumber = "9999999999";
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.getAccountByNumber(accountNumber))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account number not found")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    // ===== getMyAccounts Tests =====

    @Test
    @DisplayName("getMyAccounts() should return only active accounts")
    void testGetMyAccounts_FilterClosedAccounts() {
        // Given
        String userId = "user-123";
        Account activeAccount = Account.builder()
                .accountId("account-active")
                .userId(userId)
                .status(AccountStatus.ACTIVE)
                .balance(BigDecimal.valueOf(1000))
                .build();

        Account closedAccount = Account.builder()
                .accountId("account-closed")
                .userId(userId)
                .status(AccountStatus.CLOSED)
                .balance(BigDecimal.valueOf(0))
                .build();

        when(accountRepository.findByUserId(userId)).thenReturn(Arrays.asList(activeAccount, closedAccount));
        when(accountMapper.toDto(activeAccount)).thenReturn(testAccountDto);

        // When
        List<AccountDto> accounts = accountService.getMyAccounts(userId);

        // Then
        assertThat(accounts).hasSize(1);
        verify(accountMapper, times(1)).toDto(any(Account.class));
    }

    // ===== Edge Cases =====

    @Test
    @DisplayName("debitAccount() should handle exact balance debit")
    void testDebitAccount_ExactBalance() {
        // Given
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .amount(BigDecimal.valueOf(1000.00))
                .transactionId("tx-exact")
                .description("Exact balance debit")
                .build();

        when(accountRepository.findByIdWithLock(testAccountId)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(testAccount);

        // When
        AccountBalanceResponse response = accountService.debitAccount(testAccountId, request);

        // Then
        assertThat(response.getNewBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(accountRepository).save(argThat(account -> 
            account.getBalance().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    @DisplayName("executeInternalTransfer() should handle same account transfer attempt")
    void testExecuteInternalTransfer_SameAccount() {
        // Given - When both IDs are same, only one account will be returned
        InternalTransferRequest request = InternalTransferRequest.builder()
                .senderAccountId("account-123")
                .receiverAccountId("account-123")
                .amount(BigDecimal.valueOf(100.00))
                .transactionId("tx-same")
                .build();

        when(accountRepository.findByIdInWithLock(anyList())).thenReturn(Arrays.asList(testAccount));

        // When & Then
        assertThatThrownBy(() -> accountService.executeInternalTransfer(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("One or both accounts not found");
    }
}

