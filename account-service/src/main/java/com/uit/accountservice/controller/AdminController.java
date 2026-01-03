package com.uit.accountservice.controller;

import com.uit.accountservice.dto.AccountDto;
import com.uit.accountservice.dto.request.UpdateAccountRequest;
import com.uit.accountservice.security.RequireRole;
import com.uit.accountservice.service.AccountService;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin Controller for Account Management.
 * Provides administrative endpoints for managing all accounts in the system.
 * All endpoints require 'admin' role.
 */
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminController {

    private final AccountService accountService;

    /**
     * Get all accounts with pagination.
     * GET /admin/accounts?page=0&size=10&sort=createdAt,desc
     *
     * @param page Page number (default: 0)
     * @param size Page size (default: 10)
     * @param sortBy Sort field (default: createdAt)
     * @param sortDirection Sort direction (default: desc)
     * @return Paginated list of accounts with user information
     */
    @GetMapping
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<Page<AccountDto>>> getAllAccounts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "desc") String sortDirection
    ) {
        // Create sort object
        Sort sort = sortDirection.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AccountDto> accounts = accountService.getAllAccountsPaged(pageable);

        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    /**
     * Get account details by ID.
     * GET /admin/accounts/{accountId}
     *
     * @param accountId Account ID
     * @return Account details with user information
     */
    @GetMapping("/{accountId}")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<AccountDto>> getAccountById(
            @PathVariable("accountId") String accountId
    ) {
        AccountDto account = accountService.getAccountById(accountId);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    /**
     * Update account information.
     * PUT /admin/accounts/{accountId}
     * Currently supports updating account status (ACTIVE, LOCKED, CLOSED).
     *
     * @param accountId Account ID to update
     * @param request Update request containing fields to update
     * @return Updated account information
     */
    @PutMapping("/{accountId}")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<AccountDto>> updateAccount(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        AccountDto updatedAccount = accountService.updateAccount(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedAccount));
    }

    /**
     * Lock an account.
     * PUT /admin/accounts/{accountId}/lock
     * Changes account status to LOCKED, preventing transactions.
     *
     * @param accountId Account ID to lock
     * @return Updated account information
     */
    @PutMapping("/{accountId}/lock")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<AccountDto>> lockAccount(
            @PathVariable("accountId") String accountId
    ) {
        AccountDto lockedAccount = accountService.lockAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(lockedAccount));
    }

    /**
     * Unlock a locked account.
     * PUT /admin/accounts/{accountId}/unlock
     * Changes account status from LOCKED to ACTIVE.
     *
     * @param accountId Account ID to unlock
     * @return Updated account information
     */
    @PutMapping("/{accountId}/unlock")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<AccountDto>> unlockAccount(
            @PathVariable("accountId") String accountId
    ) {
        AccountDto unlockedAccount = accountService.unlockAccount(accountId);
        return ResponseEntity.ok(ApiResponse.success(unlockedAccount));
    }
}
