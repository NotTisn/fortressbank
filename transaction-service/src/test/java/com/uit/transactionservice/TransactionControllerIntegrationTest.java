package com.uit.transactionservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.sharedkernel.api.ApiResponse;
import com.uit.transactionservice.config.TestSecurityConfig;
import com.uit.transactionservice.dto.VerifyOTPRequest;
import com.uit.transactionservice.dto.request.CreateTransferRequest;
import com.uit.transactionservice.dto.response.TransactionResponse;
import com.uit.transactionservice.entity.Transaction;
import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.entity.TransactionType;
import com.uit.transactionservice.entity.SagaStep;
import com.uit.transactionservice.entity.TransferType;
import com.uit.transactionservice.repository.TransactionRepository;
import com.uit.transactionservice.client.AccountServiceClient;
import com.uit.transactionservice.service.OTPService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Transaction Controller.
 * Tests focus on API endpoints with mocked external dependencies.
 */
@Import(TestSecurityConfig.class)
@AutoConfigureMockMvc
@DisplayName("Transaction Controller Integration Tests")
class TransactionControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    @WithMockUser(username = "test-user-id", roles = {"user"})
    void shouldGetTransactionById() throws Exception {
        // Given: A transaction exists in the database
        Transaction transaction = createTestTransaction();
        transaction = transactionRepository.saveAndFlush(transaction);

        // When: Request to get transaction by ID
        mockMvc.perform(get("/transactions/{txId}", transaction.getTransactionId())
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: Should return transaction successfully
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("Success"))
                .andExpect(jsonPath("$.data.transactionId").value(transaction.getTransactionId().toString()))
                .andExpect(jsonPath("$.data.senderAccountNumber").value("1234567890"))
                .andExpect(jsonPath("$.data.receiverAccountNumber").value("0987654321"))
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should get transaction history for account number")
    @WithMockUser(username = "test-user-id", roles = {"user"})
    void shouldGetAccountTransactionHistory() throws Exception {
        // Given: Multiple transactions exist
        Transaction tx1 = createTestTransaction();
        tx1.setSenderAccountNumber("1234567890");
        tx1.setAmount(new BigDecimal("100.00"));
        transactionRepository.saveAndFlush(tx1);

        Transaction tx2 = createTestTransaction();
        tx2.setSenderAccountNumber("1234567890");
        tx2.setAmount(new BigDecimal("200.00"));
        transactionRepository.saveAndFlush(tx2);

        // When: Request transaction history
        mockMvc.perform(get("/transactions/{accountNumber}/history", "1234567890")
                        .param("offset", "0")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: Should return paginated history
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("Should filter transaction history by type (SENT)")
    @WithMockUser(username = "test-user-id", roles = {"user"})
    void shouldFilterTransactionHistoryBySent() throws Exception {
        // Given: Transactions with different types
        Transaction sentTx = createTestTransaction();
        sentTx.setSenderAccountNumber("1234567890");
        sentTx.setReceiverAccountNumber("0987654321");
        transactionRepository.saveAndFlush(sentTx);

        Transaction receivedTx = createTestTransaction();
        receivedTx.setSenderAccountNumber("9999999999");
        receivedTx.setReceiverAccountNumber("1234567890");
        transactionRepository.saveAndFlush(receivedTx);

        // When: Request SENT transactions
        mockMvc.perform(get("/transactions/{accountNumber}/history", "1234567890")
                        .param("type", "SENT")
                        .param("offset", "0")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: Should return only sent transactions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Should filter transaction history by type (RECEIVED)")
    @WithMockUser(username = "test-user-id", roles = {"user"})
    void shouldFilterTransactionHistoryByReceived() throws Exception {
        // Given: Transactions with different types
        Transaction sentTx = createTestTransaction();
        sentTx.setSenderAccountNumber("1234567890");
        sentTx.setReceiverAccountNumber("0987654321");
        transactionRepository.saveAndFlush(sentTx);

        Transaction receivedTx = createTestTransaction();
        receivedTx.setSenderAccountNumber("9999999999");
        receivedTx.setReceiverAccountNumber("1234567890");
        transactionRepository.saveAndFlush(receivedTx);

        // When: Request RECEIVED transactions
        mockMvc.perform(get("/transactions/{accountNumber}/history", "1234567890")
                        .param("type", "RECEIVED")
                        .param("offset", "0")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: Should return only received transactions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Should validate pagination parameters")
    @WithMockUser(username = "test-user-id", roles = {"user"})
    void shouldValidatePaginationParameters() throws Exception {
        // Given: Transaction exists
        Transaction tx = createTestTransaction();
        transactionRepository.saveAndFlush(tx);

        // When: Request with custom pagination
        mockMvc.perform(get("/transactions/{accountNumber}/history", "1234567890")
                        .param("offset", "0")
                        .param("limit", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: Should respect pagination parameters
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    @DisplayName("Should return empty list when no transactions found")
    @WithMockUser(username = "test-user-id", roles = {"user"})
    void shouldReturnEmptyListWhenNoTransactionsFound() throws Exception {
        // When: Request history for account with no transactions
        mockMvc.perform(get("/transactions/{accountNumber}/history", "9999999999")
                        .param("offset", "0")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                // Then: Should return empty list
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
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
