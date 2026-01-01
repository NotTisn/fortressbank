package com.uit.transactionservice.controller;

import com.uit.sharedkernel.api.ApiResponse;
import com.uit.transactionservice.dto.request.UpdateFeeRequest;
import com.uit.transactionservice.dto.response.TransactionFeeResponse;
import com.uit.transactionservice.entity.TransactionType;
import com.uit.transactionservice.security.RequireRole;
import com.uit.transactionservice.service.TransactionFeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions/admin")
@RequiredArgsConstructor
public class TransactionAdminController {

    private final TransactionFeeService transactionFeeService;
    private final com.uit.transactionservice.service.TransactionService transactionService;

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
}
