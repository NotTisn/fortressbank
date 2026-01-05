package com.uit.transactionservice;

import com.uit.transactionservice.config.TestSecurityConfig;
import com.uit.transactionservice.entity.*;
import com.uit.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Transaction Repository.
 * Tests database operations and custom queries.
 */
@Import(TestSecurityConfig.class)
@DisplayName("Transaction Repository Integration Tests")
class TransactionRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and retrieve transaction successfully")
    void shouldSaveAndRetrieveTransaction() {
        // Given
        Transaction transaction = createTestTransaction();

        // When
        Transaction saved = transactionRepository.saveAndFlush(transaction);

        // Then
        assertThat(saved.getTransactionId()).isNotNull();
        
        Optional<Transaction> retrieved = transactionRepository.findById(saved.getTransactionId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getSenderAccountNumber()).isEqualTo("1234567890");
        assertThat(retrieved.get().getReceiverAccountNumber()).isEqualTo("0987654321");
        assertThat(retrieved.get().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should find transactions by sender account number")
    void shouldFindTransactionsBySenderAccountNumber() {
        // Given: Multiple transactions
        Transaction tx1 = createTestTransaction();
        tx1.setSenderAccountNumber("1234567890");
        tx1.setAmount(new BigDecimal("100.00"));
        transactionRepository.saveAndFlush(tx1);

        Transaction tx2 = createTestTransaction();
        tx2.setSenderAccountNumber("1234567890");
        tx2.setAmount(new BigDecimal("200.00"));
        transactionRepository.saveAndFlush(tx2);

        Transaction tx3 = createTestTransaction();
        tx3.setSenderAccountNumber("9999999999");
        transactionRepository.saveAndFlush(tx3);

        // When: Find by sender account number
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Transaction> result = transactionRepository.findBySenderAccountNumber("1234567890", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(tx -> tx.getSenderAccountNumber().equals("1234567890"));
    }

    @Test
    @DisplayName("Should find transactions by receiver account number")
    void shouldFindTransactionsByReceiverAccountNumber() {
        // Given
        Transaction tx1 = createTestTransaction();
        tx1.setReceiverAccountNumber("0987654321");
        transactionRepository.saveAndFlush(tx1);

        Transaction tx2 = createTestTransaction();
        tx2.setReceiverAccountNumber("0987654321");
        transactionRepository.saveAndFlush(tx2);

        Transaction tx3 = createTestTransaction();
        tx3.setReceiverAccountNumber("1111111111");
        transactionRepository.saveAndFlush(tx3);

        // When
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Transaction> result = transactionRepository.findByReceiverAccountNumber("0987654321", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(tx -> tx.getReceiverAccountNumber().equals("0987654321"));
    }

    @Test
    @DisplayName("Should find transactions by status")
    void shouldFindTransactionsByStatus() {
        // Given
        Transaction pendingTx = createTestTransaction();
        pendingTx.setStatus(TransactionStatus.PENDING);
        transactionRepository.saveAndFlush(pendingTx);

        Transaction completedTx = createTestTransaction();
        completedTx.setStatus(TransactionStatus.COMPLETED);
        transactionRepository.saveAndFlush(completedTx);

        Transaction failedTx = createTestTransaction();
        failedTx.setStatus(TransactionStatus.FAILED);
        transactionRepository.saveAndFlush(failedTx);

        // When
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Transaction> completedResult = transactionRepository.findByStatus(TransactionStatus.COMPLETED, pageable);

        // Then
        assertThat(completedResult.getContent()).hasSize(1);
        assertThat(completedResult.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should find transactions by account number (sender or receiver)")
    void shouldFindTransactionsByAccountNumber() {
        // Given: Account "1234567890" is both sender and receiver in different transactions
        Transaction sentTx = createTestTransaction();
        sentTx.setSenderAccountNumber("1234567890");
        sentTx.setReceiverAccountNumber("9999999999");
        transactionRepository.saveAndFlush(sentTx);

        Transaction receivedTx = createTestTransaction();
        receivedTx.setSenderAccountNumber("8888888888");
        receivedTx.setReceiverAccountNumber("1234567890");
        transactionRepository.saveAndFlush(receivedTx);

        // When
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Transaction> result = transactionRepository.findBySenderAccountNumberOrReceiverAccountNumber(
                "1234567890", "1234567890", pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should find transactions by created date range")
    void shouldFindTransactionsByCreatedDateRange() throws InterruptedException {
        // Given: Create some transactions
        LocalDateTime start = LocalDateTime.now();
        
        Transaction tx1 = createTestTransaction();
        transactionRepository.saveAndFlush(tx1);
        Thread.sleep(100);
        
        Transaction tx2 = createTestTransaction();
        transactionRepository.saveAndFlush(tx2);
        Thread.sleep(100);
        
        LocalDateTime end = LocalDateTime.now();

        // When: Find transactions in date range
        List<Transaction> result = transactionRepository.findByCreatedAtBetween(start, end);

        // Then: Should find both transactions
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should count all transactions")
    void shouldCountAllTransactions() {
        // Given
        transactionRepository.saveAndFlush(createTransactionWithStatus(TransactionStatus.PENDING));
        transactionRepository.saveAndFlush(createTransactionWithStatus(TransactionStatus.PENDING));
        transactionRepository.saveAndFlush(createTransactionWithStatus(TransactionStatus.COMPLETED));
        transactionRepository.saveAndFlush(createTransactionWithStatus(TransactionStatus.FAILED));

        // When
        long totalCount = transactionRepository.count();

        // Then
        assertThat(totalCount).isEqualTo(4);
    }

    @Test
    @DisplayName("Should check if transaction exists by external transaction ID")
    void shouldCheckExistenceByExternalTransactionId() {
        // Given
        Transaction transaction = createTestTransaction();
        transaction.setExternalTransactionId("EXT-TX-12345");
        transactionRepository.saveAndFlush(transaction);

        // When
        boolean exists = transactionRepository.existsByExternalTransactionId("EXT-TX-12345");
        boolean notExists = transactionRepository.existsByExternalTransactionId("NON-EXISTENT");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should sort transactions by created date")
    void shouldSortTransactionsByCreatedDate() throws InterruptedException {
        // Given: Transactions created at different times
        Transaction tx1 = createTestTransaction();
        tx1.setAmount(new BigDecimal("100.00"));
        transactionRepository.saveAndFlush(tx1);
        Thread.sleep(100); // Delay to ensure different timestamps

        Transaction tx2 = createTestTransaction();
        tx2.setAmount(new BigDecimal("200.00"));
        transactionRepository.saveAndFlush(tx2);
        Thread.sleep(100);

        Transaction tx3 = createTestTransaction();
        tx3.setAmount(new BigDecimal("300.00"));
        transactionRepository.saveAndFlush(tx3);

        // When: Find all sorted by created date descending
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        Page<Transaction> result = transactionRepository.findAll(pageable);

        // Then: Most recent transaction should be first
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(result.getContent().get(1).getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getContent().get(2).getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should handle pagination correctly")
    void shouldHandlePaginationCorrectly() {
        // Given: 15 transactions
        for (int i = 0; i < 15; i++) {
            Transaction tx = createTestTransaction();
            tx.setAmount(new BigDecimal(i * 10));
            transactionRepository.saveAndFlush(tx);
        }

        // When: Request page 0 with size 10
        Pageable page0 = PageRequest.of(0, 10, Sort.by("createdAt").ascending());
        Page<Transaction> result0 = transactionRepository.findAll(page0);

        // Then: First page should have 10 items
        assertThat(result0.getContent()).hasSize(10);
        assertThat(result0.getTotalElements()).isEqualTo(15);
        assertThat(result0.getTotalPages()).isEqualTo(2);
        assertThat(result0.hasNext()).isTrue();

        // When: Request page 1 with size 10
        Pageable page1 = PageRequest.of(1, 10, Sort.by("createdAt").ascending());
        Page<Transaction> result1 = transactionRepository.findAll(page1);

        // Then: Second page should have 5 items
        assertThat(result1.getContent()).hasSize(5);
        assertThat(result1.getTotalElements()).isEqualTo(15);
        assertThat(result1.hasNext()).isFalse();
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

    private Transaction createTransactionWithStatus(TransactionStatus status) {
        Transaction tx = createTestTransaction();
        tx.setStatus(status);
        return tx;
    }
}
