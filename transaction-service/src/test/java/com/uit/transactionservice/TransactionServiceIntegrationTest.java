package com.uit.transactionservice;

import com.uit.transactionservice.config.TestSecurityConfig;
import com.uit.transactionservice.entity.*;
import com.uit.transactionservice.dto.response.TransactionResponse;
import com.uit.transactionservice.repository.TransactionRepository;
import com.uit.transactionservice.service.TransactionService;
import com.uit.transactionservice.client.AccountServiceClient;
import com.uit.transactionservice.service.OTPService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Transaction Service.
 * Tests business logic with real database but mocked external dependencies.
 */
@Import(TestSecurityConfig.class)
@DisplayName("Transaction Service Integration Tests")
class TransactionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private AccountServiceClient accountServiceClient;

    @MockBean
    private OTPService otpService;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should get transaction by ID successfully")
    void shouldGetTransactionById() {
        // Given: A transaction exists
        Transaction transaction = createTestTransaction();
        transaction = transactionRepository.saveAndFlush(transaction);

        // When: Get transaction by ID
        TransactionResponse response = transactionService.getTransactionById(transaction.getTransactionId());

        // Then: Should return transaction details
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(transaction.getTransactionId());
        assertThat(response.getSenderAccountNumber()).isEqualTo("1234567890");
        assertThat(response.getReceiverAccountNumber()).isEqualTo("0987654321");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw exception when transaction not found")
    void shouldThrowExceptionWhenTransactionNotFound() {
        // Given: Non-existent transaction ID
        UUID nonExistentId = UUID.randomUUID();

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.getTransactionById(nonExistentId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should get transaction history for account number")
    void shouldGetTransactionHistory() {
        // Given: Multiple transactions for an account
        Transaction tx1 = createTestTransaction();
        tx1.setSenderAccountNumber("1234567890");
        tx1.setAmount(new BigDecimal("100.00"));
        transactionRepository.saveAndFlush(tx1);

        Transaction tx2 = createTestTransaction();
        tx2.setSenderAccountNumber("1234567890");
        tx2.setAmount(new BigDecimal("200.00"));
        transactionRepository.saveAndFlush(tx2);

        Transaction tx3 = createTestTransaction();
        tx3.setReceiverAccountNumber("1234567890");
        tx3.setAmount(new BigDecimal("300.00"));
        transactionRepository.saveAndFlush(tx3);

        // When: Get transaction history
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionResponse> response = transactionService.getTransactionHistory("1234567890", null, pageable);

        // Then: Should return all transactions for the account
        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should filter transaction history by SENT type")
    void shouldFilterTransactionHistoryBySent() {
        // Given: Account has both sent and received transactions
        Transaction sentTx = createTestTransaction();
        sentTx.setSenderAccountNumber("1234567890");
        sentTx.setReceiverAccountNumber("9999999999");
        transactionRepository.saveAndFlush(sentTx);

        Transaction receivedTx = createTestTransaction();
        receivedTx.setSenderAccountNumber("8888888888");
        receivedTx.setReceiverAccountNumber("1234567890");
        transactionRepository.saveAndFlush(receivedTx);

        // When: Get only SENT transactions
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionResponse> response = transactionService.getTransactionHistory("1234567890", "SENT", pageable);

        // Then: Should return only sent transactions
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getSenderAccountNumber()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("Should filter transaction history by RECEIVED type")
    void shouldFilterTransactionHistoryByReceived() {
        // Given: Account has both sent and received transactions
        Transaction sentTx = createTestTransaction();
        sentTx.setSenderAccountNumber("1234567890");
        sentTx.setReceiverAccountNumber("9999999999");
        transactionRepository.saveAndFlush(sentTx);

        Transaction receivedTx = createTestTransaction();
        receivedTx.setSenderAccountNumber("8888888888");
        receivedTx.setReceiverAccountNumber("1234567890");
        transactionRepository.saveAndFlush(receivedTx);

        // When: Get only RECEIVED transactions
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionResponse> response = transactionService.getTransactionHistory("1234567890", "RECEIVED", pageable);

        // Then: Should return only received transactions
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getReceiverAccountNumber()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("Should get transaction history by status")
    void shouldGetTransactionHistoryByStatus() {
        // Given: Transactions with different statuses
        Transaction pendingTx = createTestTransaction();
        pendingTx.setStatus(TransactionStatus.PENDING);
        transactionRepository.saveAndFlush(pendingTx);

        Transaction completedTx = createTestTransaction();
        completedTx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.saveAndFlush(completedTx);

        Transaction failedTx = createTestTransaction();
        failedTx.setStatus(TransactionStatus.FAILED);
        transactionRepository.saveAndFlush(failedTx);

        // When: Get only COMPLETED transactions
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionResponse> response = transactionService.getTransactionHistoryByStatus(
                TransactionStatus.COMPLETED, pageable);

        // Then: Should return only completed transactions
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should handle pagination correctly")
    void shouldHandlePaginationCorrectly() {
        // Given: 15 transactions for an account
        for (int i = 0; i < 15; i++) {
            Transaction tx = createTestTransaction();
            tx.setSenderAccountNumber("1234567890");
            tx.setAmount(new BigDecimal(i * 10));
            transactionRepository.saveAndFlush(tx);
        }

        // When: Request page 0 with size 10
        Pageable page0 = PageRequest.of(0, 10);
        Page<TransactionResponse> response0 = transactionService.getTransactionHistory("1234567890", null, page0);

        // Then: First page should have 10 items
        assertThat(response0.getContent()).hasSize(10);
        assertThat(response0.getTotalElements()).isEqualTo(15);
        assertThat(response0.getTotalPages()).isEqualTo(2);
        assertThat(response0.hasNext()).isTrue();

        // When: Request page 1 with size 10
        Pageable page1 = PageRequest.of(1, 10);
        Page<TransactionResponse> response1 = transactionService.getTransactionHistory("1234567890", null, page1);

        // Then: Second page should have 5 items
        assertThat(response1.getContent()).hasSize(5);
        assertThat(response1.hasNext()).isFalse();
    }

    @Test
    @DisplayName("Should return empty page when no transactions found")
    void shouldReturnEmptyPageWhenNoTransactionsFound() {
        // When: Get transaction history for account with no transactions
        Pageable pageable = PageRequest.of(0, 10);
        Page<TransactionResponse> response = transactionService.getTransactionHistory("9999999999", null, pageable);

        // Then: Should return empty page
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should sort transactions by created date descending")
    void shouldSortTransactionsByCreatedDateDescending() throws InterruptedException {
        // Given: Transactions created at different times
        Transaction oldTx = createTestTransaction();
        oldTx.setSenderAccountNumber("1234567890");
        oldTx.setAmount(new BigDecimal("100.00"));
        transactionRepository.saveAndFlush(oldTx);
        Thread.sleep(100); // Delay to ensure different timestamps

        Transaction middleTx = createTestTransaction();
        middleTx.setSenderAccountNumber("1234567890");
        middleTx.setAmount(new BigDecimal("200.00"));
        transactionRepository.saveAndFlush(middleTx);
        Thread.sleep(100);

        Transaction newTx = createTestTransaction();
        newTx.setSenderAccountNumber("1234567890");
        newTx.setAmount(new BigDecimal("300.00"));
        transactionRepository.saveAndFlush(newTx);

        // When: Get transaction history sorted by created date descending
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<TransactionResponse> response = transactionService.getTransactionHistory("1234567890", null, pageable);

        // Then: Most recent transaction should be first
        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getContent().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(response.getContent().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(response.getContent().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ========== Helper Methods ==========

    private Transaction createTestTransaction() {
        return Transaction.builder()
                .senderAccountId("sender-account-id")
                .senderAccountNumber("1234567890")
                .senderUserId("sender-user-id")
                .receiverAccountId("receiver-account-id")
                .receiverAccountNumber("0987654321")
                .receiverUserId("receiver-user-id")
                .amount(new BigDecimal("100.00"))
                .feeAmount(BigDecimal.ZERO)
                .description("Test transfer")
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING)
                .currentStep(SagaStep.STARTED)
                .correlationId(UUID.randomUUID().toString())
                .build();
    }
}
