package com.uit.transactionservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.transactionservice.dto.request.DashboardStatisticsRequest;
import com.uit.transactionservice.dto.request.UpdateFeeRequest;
import com.uit.transactionservice.dto.response.DashboardStatisticsResponse;
import com.uit.transactionservice.dto.response.TransactionFeeResponse;
import com.uit.transactionservice.entity.TimePeriod;
import com.uit.transactionservice.entity.TransactionType;
import com.uit.transactionservice.security.RequireRole;
import com.uit.transactionservice.service.DashboardService;
import com.uit.transactionservice.service.TransactionFeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/transactions/admin")
@RequiredArgsConstructor
public class TransactionAdminController {

    private final TransactionFeeService transactionFeeService;
    private final com.uit.transactionservice.service.TransactionService transactionService;
    private final DashboardService dashboardService;

    /**
     * Admin Deposit (Top-up) to an account
     * POST /transactions/admin/deposit
     */
    @PostMapping("/deposit")
    // @RequireRole("admin")
    public ResponseEntity<ApiResponse<com.uit.transactionservice.dto.response.TransactionResponse>> adminDeposit(
            @Valid @RequestBody com.uit.transactionservice.dto.request.AdminDepositRequest request) {

        com.uit.transactionservice.dto.response.TransactionResponse response = transactionService.createAdminDeposit(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get all fee configurations
     * GET /transactions/admin/fees
     */
    @GetMapping("/fees")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<List<TransactionFeeResponse>>> getAllFees() {
        List<TransactionFeeResponse> fees = transactionFeeService.getAllFees();
        return ResponseEntity.ok(ApiResponse.success(fees));
    }

    /**
     * Update fee configuration for a specific transaction type
     * PUT /transactions/admin/fees/{transactionType}
     */
    @PutMapping("/fees/{transactionType}")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<TransactionFeeResponse>> updateFee(
            @PathVariable TransactionType transactionType,
            @Valid @RequestBody UpdateFeeRequest request) {

        TransactionFeeResponse response = transactionFeeService.updateFee(transactionType, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get dashboard statistics for admin
     * GET /transactions/admin/dashboard/statistics
     *
     * Query parameters:
     * - period: TODAY | THIS_WEEK | THIS_MONTH | THIS_YEAR | CUSTOM (default: TODAY)
     * - startDate: ISO 8601 datetime for CUSTOM period (required if period=CUSTOM)
     * - endDate: ISO 8601 datetime for CUSTOM period (required if period=CUSTOM)
     *
     * Examples:
     * - GET /transactions/admin/dashboard/statistics
     * - GET /transactions/admin/dashboard/statistics?period=THIS_MONTH
     * - GET /transactions/admin/dashboard/statistics?period=CUSTOM&startDate=2026-01-01T00:00:00&endDate=2026-01-05T23:59:59
     */
    @GetMapping("/dashboard/statistics")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<DashboardStatisticsResponse>> getDashboardStatistics(
            @RequestParam(required = false) TimePeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        // Default to TODAY if no period specified
        TimePeriod requestedPeriod = (period != null) ? period : TimePeriod.TODAY;

        DashboardStatisticsRequest request = DashboardStatisticsRequest.builder()
                .period(requestedPeriod)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        DashboardStatisticsResponse response = dashboardService.getStatistics(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
