package com.uit.accountservice.controller;

import com.uit.accountservice.client.UserClient;
import com.uit.accountservice.dto.AccountDto;
import com.uit.accountservice.dto.request.AdminCreateAccountRequest;
import com.uit.accountservice.dto.request.AdminUpdatePinRequest;
import com.uit.accountservice.dto.request.CreateAccountRequest;
import com.uit.accountservice.dto.request.UpdateAccountRequest;
import com.uit.accountservice.dto.response.UserResponse;
import com.uit.accountservice.security.RequireRole;
import com.uit.accountservice.service.AccountService;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
public class AdminController {

    private final AccountService accountService;
    private final UserClient userClient;

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

    /**
     * Create account for a user (Admin operation).
     * POST /admin/accounts
     * Admin can create account for any user with specified userId.
     * 
     * This endpoint:
     * - Validates phone number uniqueness (only 1 account per phone)
     * - Fetches user's full name from user-service
     * - Creates account with optional PIN
     * - Automatically creates a card for the account
     *
     * @param request Admin create account request with userId, accountNumberType, phoneNumber, pin
     * @return Created account information
     */
    @PostMapping
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<AccountDto>> createAccountForUser(
            @Valid @RequestBody AdminCreateAccountRequest request
    ) {
        log.info("Admin creating account for userId: {}", request.userId());

        CreateAccountRequest createRequest = new CreateAccountRequest(
                request.accountNumberType(),
                request.phoneNumber(),
                request.pin()
        );
        
        // Fetch user's full name from user-service for card creation
        String fullName = null;
        try {
            ApiResponse<UserResponse> userResponse = userClient.getUserById(request.userId());
            if (userResponse != null && userResponse.getData() != null) {
                fullName = userResponse.getData().fullName();
                log.info("Fetched fullName for userId {}: {}", request.userId(), fullName);
            } else {
                log.warn("User not found or no data returned for userId: {}", request.userId());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user info for userId {}: {}", request.userId(), e.getMessage());
            // Continue with null fullName - createAccount will handle it
        }
        
        AccountDto newAccount = accountService.adminCreateAccount(
                request.userId(), 
                createRequest, 
                fullName
        );
        
        log.info("Admin successfully created account {} for user {}", newAccount.getAccountId(), request.userId());
        return ResponseEntity.ok(ApiResponse.success(newAccount));
    }

    /**
     * Update PIN for an account (Admin operation).
     * PUT /admin/accounts/{accountId}/pin
     * Admin can update PIN without requiring old PIN verification.
     *
     * @param accountId Account ID to update PIN
     * @param request Admin update PIN request with newPin
     * @return Success response
     */
    @PutMapping("/{accountId}/pin")
    @RequireRole("admin")
    public ResponseEntity<ApiResponse<Void>> updateAccountPin(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody AdminUpdatePinRequest request
    ) {
        accountService.adminUpdatePin(accountId, request.newPin());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
