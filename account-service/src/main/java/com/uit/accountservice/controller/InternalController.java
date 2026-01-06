package com.uit.accountservice.controller;

import com.uit.accountservice.dto.response.AccountStatisticsResponse;
import com.uit.accountservice.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal Controller for service-to-service communication
 * These endpoints are NOT exposed via Kong Gateway
 * Protected by network policy in production
 */
@Slf4j
@RestController
@RequestMapping("/accounts/internal")
@RequiredArgsConstructor
public class InternalController {

    private final AccountService accountService;

    /**
     * Get account statistics for dashboard
     * GET /accounts/internal/statistics
     * Called by transaction-service for admin dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<AccountStatisticsResponse> getAccountStatistics() {
        log.info("Internal request: Fetching account statistics");
        AccountStatisticsResponse statistics = accountService.getAccountStatistics();
        return ResponseEntity.ok(statistics);
    }
}
