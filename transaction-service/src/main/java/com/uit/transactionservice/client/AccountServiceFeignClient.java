package com.uit.transactionservice.client;

import com.uit.transactionservice.client.dto.AccountBalanceRequest;
import com.uit.transactionservice.client.dto.AccountBalanceResponse;
import com.uit.transactionservice.client.dto.InternalTransferRequest;
import com.uit.transactionservice.client.dto.InternalTransferResponse;
import com.uit.transactionservice.dto.response.AccountStatisticsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * FeignClient for Account Service communication
 * Uses Eureka service discovery or direct URL to locate account-service
 *
 * contextId is required when multiple FeignClients target the same service
 */
@FeignClient(name = "account-service", url = "${services.account-service.url:http://localhost:4001}", contextId = "accountServiceClient")
public interface AccountServiceFeignClient {

    /**
     * Debit (subtract) amount from an account
     * Internal endpoint - service-to-service communication only
     */
    @PostMapping("/accounts/internal/{accountId}/debit")
    AccountBalanceResponse debitAccount(
            @PathVariable("accountId") String accountId,
            @RequestBody AccountBalanceRequest request
    );

    /**
     * Credit (add) amount to an account
     */
    @PostMapping("/accounts/internal/{accountId}/credit")
    AccountBalanceResponse creditAccount(
            @PathVariable("accountId") String accountId,
            @RequestBody AccountBalanceRequest request
    );

    /**
     * Get account details by account number
     */
    @GetMapping("/accounts/internal/by-number/{accountNumber}")
    ResponseEntity<Map<String, Object>> getAccountByNumber(
            @PathVariable("accountNumber") String accountNumber
    );

    /**
     * Get account details by account ID
     */
    @GetMapping("/accounts/{accountId}")
    ResponseEntity<Map<String, Object>> getAccountById(
            @PathVariable("accountId") String accountId
    );

    /**
     * Execute internal transfer atomically
     * Both debit and credit happen in a single database transaction
     * Internal endpoint - service-to-service communication only
     */
    @PostMapping("/accounts/internal/transfer")
    InternalTransferResponse executeInternalTransfer(
            @RequestBody InternalTransferRequest request
    );

    /**
     * Get account statistics (total, active, locked, closed counts)
     * Internal endpoint - service-to-service communication only
     */
    @GetMapping("/accounts/internal/statistics")
    AccountStatisticsDto getAccountStatistics();
}
