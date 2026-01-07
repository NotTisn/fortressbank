package com.uit.transactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.notification.NotificationEventPublisher;
import com.uit.sharedkernel.outbox.repository.OutboxEventRepository;
import com.uit.transactionservice.client.AccountServiceClient;
import com.uit.transactionservice.client.RiskEngineClient;
import com.uit.transactionservice.client.dto.AccountBalanceResponse;
import com.uit.transactionservice.client.dto.InternalTransferResponse;
import com.uit.transactionservice.client.dto.RiskAssessmentResponse;
import com.uit.transactionservice.dto.request.CreateTransferRequest;
import com.uit.transactionservice.dto.response.TransactionResponse;
import com.uit.transactionservice.entity.Transaction;
import com.uit.transactionservice.entity.TransactionLimit;
import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.entity.TransactionType;
import com.uit.transactionservice.exception.InsufficientBalanceException;
import com.uit.transactionservice.mapper.TransactionMapper;
import com.uit.transactionservice.repository.TransactionFeeRepository;
import com.uit.transactionservice.repository.TransactionLimitRepository;
import com.uit.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionService
 * Comprehensive tests for transaction creation, OTP verification, and transfer processing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TransactionFeeRepository transactionFeeRepository;

    @Mock
    private TransactionLimitRepository transactionLimitRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OTPService otpService;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Mock
    private StripeTransferService stripeTransferService;

    @Mock
    private TransactionSseService sseService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private RiskEngineClient riskEngineClient;

    @InjectMocks
    private TransactionService transactionService;

    private CreateTransferRequest validTransferRequest;
    private Transaction testTransaction;
    private TransactionResponse testTransactionResponse;
    private UUID testTransactionId;
    private TransactionLimit testTransactionLimit;

    @BeforeEach
    void setUp() {
        testTransactionId = UUID.randomUUID();

        validTransferRequest = CreateTransferRequest.builder()
                .senderAccountId("account-123")
                .senderAccountNumber("1234567890")
                .receiverAccountNumber("9876543210")
                .amount(BigDecimal.valueOf(100.00))
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .description("Test transfer")
                .build();

        testTransaction = Transaction.builder()
                .transactionId(testTransactionId)
                .senderAccountId("account-123")
                .senderAccountNumber("1234567890")
                .senderUserId("user-123")
                .receiverAccountNumber("9876543210")
                .receiverAccountId("account-456")
                .receiverUserId("user-456")
                .amount(BigDecimal.valueOf(100.00))
                .feeAmount(BigDecimal.ZERO)
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING_OTP)
                .description("Test transfer")
                .build();

        testTransactionResponse = TransactionResponse.builder()
                .transactionId(testTransactionId)
                .senderAccountId("account-123")
                .receiverAccountId("account-456")
                .amount(BigDecimal.valueOf(100.00))
                .status(TransactionStatus.PENDING_OTP)
                .requireFaceAuth(false)
                .build();

        // Setup default TransactionLimit
        testTransactionLimit = TransactionLimit.builder()
                .accountId("account-123")
                .dailyLimit(BigDecimal.valueOf(50000))
                .monthlyLimit(BigDecimal.valueOf(200000))
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .lastDailyReset(LocalDateTime.now())
                .lastMonthlyReset(LocalDateTime.now())
                .build();
    }

    /**
     * Helper method to setup TransactionLimit mock for tests that need it
     */
    private void setupTransactionLimitMock() {
        lenient().when(transactionLimitRepository.findById(anyString()))
                .thenReturn(Optional.of(testTransactionLimit));
        lenient().when(transactionLimitRepository.save(any(TransactionLimit.class)))
                .thenReturn(testTransactionLimit);
    }

    // ===== createTransfer Tests =====

    @Test
    @DisplayName("createTransfer() should create transaction successfully with LOW risk (OTP flow)")
    void testCreateTransfer_Success_LowRisk() {
        // Given
        setupTransactionLimitMock();
        
        Map<String, Object> senderInfo = new HashMap<>();
        senderInfo.put("userId", "user-123");
        
        Map<String, Object> receiverInfo = new HashMap<>();
        receiverInfo.put("accountId", "account-456");
        receiverInfo.put("userId", "user-456");

        RiskAssessmentResponse lowRiskResponse = new RiskAssessmentResponse();
        lowRiskResponse.setRiskLevel("LOW");
        lowRiskResponse.setChallengeType("NONE");

        when(accountServiceClient.getAccountByNumber("1234567890")).thenReturn(senderInfo);
        when(accountServiceClient.getAccountByNumber("9876543210")).thenReturn(receiverInfo);
        when(riskEngineClient.assessTransactionRisk(anyString(), any(BigDecimal.class), any(), any(), any())).thenReturn(lowRiskResponse);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);
        when(otpService.generateOTP()).thenReturn("123456");

        // When
        TransactionResponse response = transactionService.createTransfer(validTransferRequest, "user-123", "+84901234567");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING_OTP);
        assertThat(response.isRequireFaceAuth()).isFalse();
        
        verify(accountServiceClient).getAccountByNumber("1234567890");
        verify(accountServiceClient).getAccountByNumber("9876543210");
        verify(riskEngineClient).assessTransactionRisk(anyString(), any(BigDecimal.class), any(), any(), any());
        verify(transactionRepository).save(any(Transaction.class));
        verify(otpService).generateOTP();
        verify(otpService).saveOTP(any(UUID.class), eq("123456"), eq("+84901234567"));
    }

    @Test
    @DisplayName("createTransfer() should require FaceAuth for HIGH risk transactions")
    void testCreateTransfer_HighRisk_RequiresFaceAuth() {
        // Given
        setupTransactionLimitMock();
        
        Map<String, Object> senderInfo = new HashMap<>();
        senderInfo.put("userId", "user-123");
        
        Map<String, Object> receiverInfo = new HashMap<>();
        receiverInfo.put("accountId", "account-456");
        receiverInfo.put("userId", "user-456");

        RiskAssessmentResponse highRiskResponse = new RiskAssessmentResponse();
        highRiskResponse.setRiskLevel("HIGH");
        highRiskResponse.setChallengeType("FACE_ID");

        testTransaction.setStatus(TransactionStatus.PENDING_FACE_AUTH);
        testTransactionResponse.setStatus(TransactionStatus.PENDING_FACE_AUTH);
        testTransactionResponse.setRequireFaceAuth(true);

        when(accountServiceClient.getAccountByNumber("1234567890")).thenReturn(senderInfo);
        when(accountServiceClient.getAccountByNumber("9876543210")).thenReturn(receiverInfo);
        when(riskEngineClient.assessTransactionRisk(anyString(), any(BigDecimal.class), any(), any(), any())).thenReturn(highRiskResponse);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        TransactionResponse response = transactionService.createTransfer(validTransferRequest, "user-123", "+84901234567");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING_FACE_AUTH);
        assertThat(response.isRequireFaceAuth()).isTrue();
        
        verify(riskEngineClient).assessTransactionRisk(anyString(), any(BigDecimal.class), any(), any(), any());
        verify(otpService, never()).generateOTP(); // OTP not generated for HIGH risk until FaceAuth
    }

    @Test
    @DisplayName("createTransfer() should throw exception when sender account not found")
    void testCreateTransfer_SenderAccountNotFound() {
        // Given
        when(accountServiceClient.getAccountByNumber("1234567890")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> transactionService.createTransfer(validTransferRequest, "user-123", "+84901234567"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Sender account validation failed");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTransfer() should throw exception when receiver account not found (INTERNAL)")
    void testCreateTransfer_ReceiverAccountNotFound_Internal() {
        // Given
        Map<String, Object> senderInfo = new HashMap<>();
        senderInfo.put("userId", "user-123");

        when(accountServiceClient.getAccountByNumber("1234567890")).thenReturn(senderInfo);
        when(accountServiceClient.getAccountByNumber("9876543210")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> transactionService.createTransfer(validTransferRequest, "user-123", "+84901234567"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Receiver account not found");

        verify(transactionRepository, never()).save(any());
    }

    // ===== verifyOTP Tests =====

    @Test
    @DisplayName("verifyOTP() should process internal transfer successfully with valid OTP")
    void testVerifyOTP_ValidOTP_InternalTransfer_Success() {
        // Given
        setupTransactionLimitMock();
        
        testTransaction.setStatus(TransactionStatus.PENDING_OTP);
        testTransaction.setTransactionType(TransactionType.INTERNAL_TRANSFER);
        
        OTPService.OTPVerificationResult successResult = OTPService.OTPVerificationResult.success();
        InternalTransferResponse transferResponse = new InternalTransferResponse();
        transferResponse.setSenderAccountNewBalance(BigDecimal.valueOf(900));
        transferResponse.setReceiverAccountNewBalance(BigDecimal.valueOf(1100));

        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(otpService.verifyOTP(testTransactionId, "123456")).thenReturn(successResult);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountServiceClient.executeInternalTransfer(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(transferResponse);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        TransactionResponse response = transactionService.verifyOTP(testTransactionId, "123456");

        // Then
        assertThat(response).isNotNull();
        verify(otpService).verifyOTP(testTransactionId, "123456");
        verify(accountServiceClient).executeInternalTransfer(anyString(), anyString(), any(), anyString(), anyString());
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("verifyOTP() should throw exception with invalid OTP")
    void testVerifyOTP_InvalidOTP() {
        // Given
        testTransaction.setStatus(TransactionStatus.PENDING_OTP);
        OTPService.OTPVerificationResult invalidResult = OTPService.OTPVerificationResult.invalid(2);

        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(otpService.verifyOTP(testTransactionId, "wrong-otp")).thenReturn(invalidResult);

        // When & Then
        assertThatThrownBy(() -> transactionService.verifyOTP(testTransactionId, "wrong-otp"))
                .isInstanceOf(AppException.class);

        verify(accountServiceClient, never()).executeInternalTransfer(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("verifyOTP() should handle expired OTP")
    void testVerifyOTP_ExpiredOTP() {
        // Given
        testTransaction.setStatus(TransactionStatus.PENDING_OTP);
        OTPService.OTPVerificationResult expiredResult = OTPService.OTPVerificationResult.expired();

        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(otpService.verifyOTP(testTransactionId, "123456")).thenReturn(expiredResult);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        TransactionResponse response = transactionService.verifyOTP(testTransactionId, "123456");

        // Then
        assertThat(response).isNotNull();
        verify(transactionRepository).save(argThat(tx -> 
            tx.getStatus() == TransactionStatus.OTP_EXPIRED
        ));
    }

    @Test
    @DisplayName("verifyOTP() should throw exception when transaction not found")
    void testVerifyOTP_TransactionNotFound() {
        // Given
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> transactionService.verifyOTP(testTransactionId, "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    @DisplayName("verifyOTP() should throw exception when transaction status is not PENDING_OTP")
    void testVerifyOTP_InvalidStatus() {
        // Given
        testTransaction.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));

        // When & Then
        assertThatThrownBy(() -> transactionService.verifyOTP(testTransactionId, "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not pending OTP verification");
    }

    // ===== getTransactionById Tests =====

    @Test
    @DisplayName("getTransactionById() should return transaction when found")
    void testGetTransactionById_Success() {
        // Given
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(transactionMapper.toResponse(testTransaction)).thenReturn(testTransactionResponse);

        // When
        TransactionResponse response = transactionService.getTransactionById(testTransactionId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(testTransactionId);
        verify(transactionRepository).findById(testTransactionId);
    }

    @Test
    @DisplayName("getTransactionById() should throw exception when transaction not found")
    void testGetTransactionById_NotFound() {
        // Given
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> transactionService.getTransactionById(testTransactionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Transaction not found");
    }

    // ===== getTransactionHistory Tests =====

    @Test
    @DisplayName("getTransactionHistory() should return sent transactions when type is SENT")
    void testGetTransactionHistory_Sent() {
        // Given
        String accountNumber = "1234567890";
        Pageable pageable = PageRequest.of(0, 10);
        List<Transaction> transactions = Arrays.asList(testTransaction);
        Page<Transaction> page = new PageImpl<>(transactions, pageable, 1);

        when(transactionRepository.findBySenderAccountNumber(accountNumber, pageable)).thenReturn(page);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        Page<TransactionResponse> result = transactionService.getTransactionHistory(accountNumber, "SENT", pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).findBySenderAccountNumber(accountNumber, pageable);
    }

    @Test
    @DisplayName("getTransactionHistory() should return received transactions when type is RECEIVED")
    void testGetTransactionHistory_Received() {
        // Given
        String accountNumber = "9876543210";
        Pageable pageable = PageRequest.of(0, 10);
        List<Transaction> transactions = Arrays.asList(testTransaction);
        Page<Transaction> page = new PageImpl<>(transactions, pageable, 1);

        when(transactionRepository.findByReceiverAccountNumber(accountNumber, pageable)).thenReturn(page);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        Page<TransactionResponse> result = transactionService.getTransactionHistory(accountNumber, "RECEIVED", pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).findByReceiverAccountNumber(accountNumber, pageable);
    }

    @Test
    @DisplayName("getTransactionHistory() should return all transactions when type is ALL")
    void testGetTransactionHistory_All() {
        // Given
        String accountNumber = "1234567890";
        Pageable pageable = PageRequest.of(0, 10);
        List<Transaction> transactions = Arrays.asList(testTransaction);
        Page<Transaction> page = new PageImpl<>(transactions, pageable, 1);

        when(transactionRepository.findBySenderAccountNumberOrReceiverAccountNumber(accountNumber, accountNumber, pageable))
                .thenReturn(page);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        Page<TransactionResponse> result = transactionService.getTransactionHistory(accountNumber, "ALL", pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).findBySenderAccountNumberOrReceiverAccountNumber(accountNumber, accountNumber, pageable);
    }

    // ===== getTransactionHistoryByStatus Tests =====

    @Test
    @DisplayName("getTransactionHistoryByStatus() should return transactions with specific status")
    void testGetTransactionHistoryByStatus() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<Transaction> transactions = Arrays.asList(testTransaction);
        Page<Transaction> page = new PageImpl<>(transactions, pageable, 1);

        when(transactionRepository.findByStatus(TransactionStatus.COMPLETED, pageable)).thenReturn(page);
        when(transactionMapper.toResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        // When
        Page<TransactionResponse> result = transactionService.getTransactionHistoryByStatus(TransactionStatus.COMPLETED, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(transactionRepository).findByStatus(TransactionStatus.COMPLETED, pageable);
    }

    // ===== resendOtp Tests =====

    @Test
    @DisplayName("resendOtp() should generate and send new OTP successfully")
    void testResendOtp_Success() {
        // Given
        testTransaction.setStatus(TransactionStatus.PENDING_OTP);
        OTPService.OTPData existingOtpData = new OTPService.OTPData("old-otp", "+84901234567", System.currentTimeMillis() - 10000);

        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(otpService.getOtpData(testTransactionId)).thenReturn(existingOtpData);
        when(otpService.generateOTP()).thenReturn("654321");

        // When
        String newOtp = transactionService.resendOtp(testTransactionId);

        // Then
        assertThat(newOtp).isEqualTo("654321");
        verify(otpService).saveOTP(testTransactionId, "654321", "+84901234567");
        verify(otpService).generateOTP();
    }

    @Test
    @DisplayName("resendOtp() should throw exception when transaction not in PENDING_OTP status")
    void testResendOtp_InvalidStatus() {
        // Given
        testTransaction.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));

        // When & Then
        assertThatThrownBy(() -> transactionService.resendOtp(testTransactionId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("resendOtp() should throw exception when OTP not found in Redis")
    void testResendOtp_OtpNotFound() {
        // Given
        testTransaction.setStatus(TransactionStatus.PENDING_OTP);
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(otpService.getOtpData(testTransactionId)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> transactionService.resendOtp(testTransactionId))
                .isInstanceOf(AppException.class);
    }

    // ===== Internal Transfer Processing Tests =====

    @Test
    @DisplayName("processInternalTransfer() should handle insufficient balance exception")
    void testProcessInternalTransfer_InsufficientBalance() {
        // Given
        setupTransactionLimitMock();
        
        testTransaction.setStatus(TransactionStatus.PENDING_OTP); // Must be PENDING_OTP to pass OTP verification
        testTransaction.setTransactionType(TransactionType.INTERNAL_TRANSFER);
        
        when(transactionRepository.findById(testTransactionId)).thenReturn(Optional.of(testTransaction));
        when(otpService.verifyOTP(testTransactionId, "123456"))
                .thenReturn(OTPService.OTPVerificationResult.success());
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);
        when(accountServiceClient.executeInternalTransfer(anyString(), anyString(), any(), anyString(), anyString()))
                .thenThrow(new InsufficientBalanceException("Insufficient balance"));

        // When & Then
        assertThatThrownBy(() -> transactionService.verifyOTP(testTransactionId, "123456"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient balance");

        verify(transactionRepository, atLeastOnce()).save(argThat(tx -> 
            tx.getStatus() == TransactionStatus.FAILED
        ));
    }
}
