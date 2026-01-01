package com.uit.transactionservice.client;

import com.uit.transactionservice.client.dto.AccountBalanceRequest;
import com.uit.transactionservice.client.dto.AccountBalanceResponse;
import com.uit.transactionservice.client.dto.InternalTransferRequest;
import com.uit.transactionservice.client.dto.InternalTransferResponse;
import com.uit.transactionservice.exception.AccountServiceException;
import com.uit.transactionservice.exception.InsufficientBalanceException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Client for synchronous communication with Account Service
 * Uses FeignClient for service discovery and inter-service communication
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountServiceClient {

    private final AccountServiceFeignClient accountServiceFeignClient;

    /**
     * Debit (subtract) amount from an account
     * This is a synchronous blocking call
     *
     * SECURITY FIX (2024-12): Updated to use /internal/ path - service-to-service only
     */
    public AccountBalanceResponse debitAccount(String accountId, BigDecimal amount, String transactionId, String description) {
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .accountId(accountId)
                .amount(amount)
                .transactionId(transactionId)
                .description(description)
                .build();

        log.info("Debiting account {} with amount {} for transaction {}", accountId, amount, transactionId);

        try {
            AccountBalanceResponse response = accountServiceFeignClient.debitAccount(accountId, request);
            log.info("Successfully debited account {} - New balance: {}", accountId, response.getNewBalance());
            return response;

        } catch (FeignException.BadRequest e) {
            log.error("Bad request while debiting account {}: {}", accountId, e.getMessage());

            // Handle specific business errors
            String responseBody = e.contentUTF8();
            if (responseBody.contains("Insufficient balance")) {
                throw new InsufficientBalanceException("Insufficient balance in account: " + accountId);
            }
            throw new AccountServiceException("Failed to debit account: " + e.getMessage(), e);

        } catch (FeignException.NotFound e) {
            log.error("Account not found: {}", accountId);
            throw new AccountServiceException("Account not found: " + accountId, e);

        } catch (FeignException.ServiceUnavailable e) {
            log.error("Account service is unavailable: {}", e.getMessage());
            throw new AccountServiceException("Account service is temporarily unavailable", e);

        } catch (FeignException e) {
            log.error("Feign error while debiting account {}: {} - {}", accountId, e.status(), e.getMessage());

            if (e.status() == 423) { // LOCKED
                throw new AccountServiceException("Account is locked: " + accountId, e);
            }
            throw new AccountServiceException("Failed to debit account: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Unexpected error while debiting account", e);
            throw new AccountServiceException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Credit (add) amount to an account
     * This is a synchronous blocking call
     */
    public AccountBalanceResponse creditAccount(String accountId, BigDecimal amount, String transactionId, String description) {
        AccountBalanceRequest request = AccountBalanceRequest.builder()
                .accountId(accountId)
                .amount(amount)
                .transactionId(transactionId)
                .description(description)
                .build();

        log.info("Crediting account {} with amount {} for transaction {}", accountId, amount, transactionId);

        try {
            AccountBalanceResponse response = accountServiceFeignClient.creditAccount(accountId, request);
            log.info("Successfully credited account {} - New balance: {}", accountId, response.getNewBalance());
            return response;

        } catch (FeignException.NotFound e) {
            log.error("Account not found: {}", accountId);
            throw new AccountServiceException("Account not found: " + accountId, e);

        } catch (FeignException.ServiceUnavailable e) {
            log.error("Account service is unavailable: {}", e.getMessage());
            throw new AccountServiceException("Account service is temporarily unavailable", e);

        } catch (FeignException e) {
            log.error("Feign error while crediting account {}: {} - {}", accountId, e.status(), e.getMessage());

            if (e.status() == 423) { // LOCKED
                throw new AccountServiceException("Account is locked: " + accountId, e);
            }
            throw new AccountServiceException("Failed to credit account: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Unexpected error while crediting account", e);
            throw new AccountServiceException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Rollback debit operation (credit back the amount)
     * Used for compensating transaction in case of failure
     */
    public void rollbackDebit(String accountId, BigDecimal amount, String transactionId) {
        log.warn("Rolling back debit for account {} - amount: {} - transaction: {}", accountId, amount, transactionId);

        try {
            creditAccount(accountId, amount, transactionId, "Rollback debit for failed transaction");
        } catch (Exception e) {
            log.error("CRITICAL: Failed to rollback debit for account {} - Manual intervention required!", accountId, e);
            // In production, this should trigger alerts for manual reconciliation
        }
    }

    /**
     * Get account details by account number.
     * Returns Map with account info if found, or null if not found (404).
     */
    public Map<String, Object> getAccountByNumber(String accountNumber) {
        log.info("Resolving account number: {}", accountNumber);

        try {
            ResponseEntity<Map<String, Object>> response = accountServiceFeignClient.getAccountByNumber(accountNumber);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            return null;

        } catch (FeignException.NotFound e) {
            log.warn("Account number not found: {}", accountNumber);
            return null;

        } catch (Exception e) {
            log.error("Failed to resolve account number {}: {}", accountNumber, e.getMessage());
            throw new AccountServiceException("Failed to resolve account number", e);
        }
    }

    /**
     * Get userId by accountId.
     * Safely returns null if account is not found or external, ensuring transaction flow continues.
     */
    public String getUserIdByAccountId(String accountId) {
        if (accountId == null) return null;

        log.debug("Resolving userId for account: {}", accountId);

        try {
            ResponseEntity<Map<String, Object>> response = accountServiceFeignClient.getAccountById(accountId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object userIdObj = response.getBody().get("userId");
                if (userIdObj != null) {
                    return userIdObj.toString();
                }
            }
        } catch (FeignException.NotFound e) {
            // Normal for external accounts
            log.warn("Account not found in local DB (likely external): {}", accountId);
        } catch (Exception e) {
            // Suppress other errors to avoid breaking the flow
            log.warn("Failed to resolve userId for account {}: {}", accountId, e.getMessage());
        }

        return null;
    }

    /**
     * Execute internal transfer atomically (RECOMMENDED).
     * Both debit and credit happen in a single database transaction.
     * Either both succeed or both fail - no partial state.
     *
     * SECURITY FIX (2024-12): Updated to use /internal/ path - service-to-service only
     */
    public InternalTransferResponse executeInternalTransfer(
            String fromAccountId,
            String toAccountId,
            BigDecimal amount,
            String transactionId,
            String description) {

        InternalTransferRequest request = InternalTransferRequest.builder()
                .transactionId(transactionId)
                .senderAccountId(fromAccountId)
                .receiverAccountId(toAccountId)
                .amount(amount)
                .description(description)
                .build();

        log.info("Executing internal transfer - From: {} To: {} Amount: {} TxID: {}",
                fromAccountId, toAccountId, amount, transactionId);

        try {
            InternalTransferResponse response = accountServiceFeignClient.executeInternalTransfer(request);

            log.info("Internal transfer completed - TxID: {} - Sender new balance: {} - Receiver new balance: {}",
                    transactionId,
                    response.getSenderAccountNewBalance(),
                    response.getReceiverAccountNewBalance());
            return response;

        } catch (FeignException.BadRequest e) {
            log.error("Bad request during internal transfer: {} - {}", e.status(), e.getMessage());

            String responseBody = e.contentUTF8();
            if (responseBody.contains("Insufficient balance")) {
                throw new InsufficientBalanceException("Insufficient balance in sender account");
            }
            throw new AccountServiceException("Failed to execute internal transfer: " + e.getMessage(), e);

        } catch (FeignException.NotFound e) {
            log.error("One or both accounts not found during internal transfer");
            throw new AccountServiceException("One or both accounts not found", e);

        } catch (FeignException.ServiceUnavailable e) {
            log.error("Account service is unavailable: {}", e.getMessage());
            throw new AccountServiceException("Account service is temporarily unavailable", e);

        } catch (FeignException e) {
            log.error("Feign error during internal transfer: {} - {}", e.status(), e.getMessage());
            throw new AccountServiceException("Failed to execute internal transfer: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Unexpected error during internal transfer", e);
            throw new AccountServiceException("Unexpected error: " + e.getMessage(), e);
        }
    }
}
