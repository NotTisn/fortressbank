package com.uit.transactionservice;

import com.uit.transactionservice.config.TestSecurityConfig;
import com.uit.transactionservice.entity.Transaction;
import com.uit.transactionservice.entity.SagaStep;
import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.entity.TransactionType;
import com.uit.transactionservice.entity.TransferType;
import com.uit.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test to verify Testcontainers setup works correctly.
 *
 * Tests:
 * - PostgreSQL container is running and accessible
 * - Redis container is running and accessible
 * - RabbitMQ container is running
 * - Transaction entity can be persisted to database
 * - Redis operations work correctly
 * 
 * Note: These tests are disabled due to Testcontainers lifecycle issues.
 * The main integration tests (TransactionRepositoryIntegrationTest, 
 * TransactionServiceIntegrationTest, TransactionControllerIntegrationTest)
 * provide sufficient coverage.
 */
@Import(TestSecurityConfig.class)
@DisplayName("Transaction Service Application Integration Test")
@Disabled("Disabled due to Testcontainers lifecycle issues - main integration tests provide coverage")
class TransactionServiceApplicationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Should start application context successfully")
    void contextLoads() {
        // Just verify Spring context loads with all containers
        assertThat(transactionRepository).isNotNull();
        assertThat(redisTemplate).isNotNull();
    }

    @Test
    @DisplayName("Should persist transaction to PostgreSQL successfully")
    void testPostgreSQLConnection() {
        // Given
        Transaction transaction = Transaction.builder()
                .senderAccountId("test-sender-account")
                .senderAccountNumber("1234567890")
                .senderUserId("test-sender-user")
                .receiverAccountId("test-receiver-account")
                .receiverAccountNumber("0987654321")
                .receiverUserId("test-receiver-user")
                .amount(new BigDecimal("100.00"))
                .feeAmount(BigDecimal.ZERO)
                .description("Test transaction")
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING)
                .currentStep(SagaStep.STARTED)
                .correlationId(UUID.randomUUID().toString())
                .build();

        // When
        Transaction saved = transactionRepository.saveAndFlush(transaction);

        // Then
        assertThat(saved.getTransactionId()).isNotNull();
        assertThat(saved.getSenderAccountId()).isEqualTo("test-sender-account");
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Cleanup
        transactionRepository.deleteById(saved.getTransactionId());
    }

    @Test
    @DisplayName("Should store and retrieve data from Redis successfully")
    void testRedisConnection() {
        // Given
        String testKey = "test:key:" + UUID.randomUUID();
        String testValue = "test-value-" + System.currentTimeMillis();

        // When
        redisTemplate.opsForValue().set(testKey, testValue);
        Object retrieved = redisTemplate.opsForValue().get(testKey);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.toString()).isEqualTo(testValue);

        // Cleanup
        redisTemplate.delete(testKey);
    }

    @Test
    @DisplayName("Should count transactions in repository")
    void testTransactionQueries() {
        // Given
        Transaction tx1 = createTestTransaction("1111111111", "sender-1");
        Transaction tx2 = createTestTransaction("2222222222", "sender-2");

        tx1 = transactionRepository.saveAndFlush(tx1);
        tx2 = transactionRepository.saveAndFlush(tx2);

        // When
        long count = transactionRepository.count();

        // Then
        assertThat(count).isGreaterThanOrEqualTo(2);

        // Cleanup
        transactionRepository.deleteById(tx1.getTransactionId());
        transactionRepository.deleteById(tx2.getTransactionId());
    }

    private Transaction createTestTransaction(String senderAccountNumber, String senderAccountId) {
        return Transaction.builder()
                .senderAccountId(senderAccountId)
                .senderAccountNumber(senderAccountNumber)
                .senderUserId("sender-user")
                .receiverAccountId("receiver-id-" + UUID.randomUUID())
                .receiverAccountNumber("receiver-number")
                .receiverUserId("receiver-user")
                .amount(new BigDecimal("50.00"))
                .feeAmount(BigDecimal.ZERO)
                .description("Test transaction")
                .transactionType(TransactionType.INTERNAL_TRANSFER)
                .status(TransactionStatus.PENDING)
                .currentStep(SagaStep.STARTED)
                .correlationId(UUID.randomUUID().toString())
                .build();
    }
}
