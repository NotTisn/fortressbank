package com.uit.accountservice.controller;

import com.uit.accountservice.dto.request.*;
import com.uit.accountservice.mapper.AccountMapper;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.accountservice.security.RequireRole;
import com.uit.accountservice.service.AccountService;
import com.uit.accountservice.service.TransferAuditService;
import com.uit.accountservice.dto.AccountDto;
import com.uit.sharedkernel.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {
    
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final TransferAuditService auditService;
    
    // change this to check health for this endpoint
    @GetMapping("/")
    public ApiResponse<Map<String, String>> healthCheck() {
        return ApiResponse.success(Map.of("status", "Account Service is running"));
    }

    @GetMapping("/my-accounts")
    @RequireRole("user")
    public ApiResponse<Map<String, Object>> getMyAccountsWithUserInfo(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new com.uit.sharedkernel.exception.AppException(com.uit.sharedkernel.exception.ErrorCode.FORBIDDEN, "User not authenticated");
        }
        
        String userId = jwt.getSubject();
        List<AccountDto> accounts = accountService.getAccountsByUserId(userId);

        // Build user info from JWT claims
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("sub", jwt.getSubject());
        userInfo.put("name", jwt.getClaimAsString("name"));
        userInfo.put("email", jwt.getClaimAsString("email"));
        userInfo.put("preferred_username", jwt.getClaimAsString("preferred_username"));

        return ApiResponse.success(Map.of(
            "message", "Your accounts",
            "user", userInfo,
            "accounts", accounts
        ));
    }

    @GetMapping("/dashboard")
    @RequireRole("admin")  // Like requireRole('admin') in Express
    public ApiResponse<Map<String, Object>> getDashboard() {
        return ApiResponse.success(Map.of(
            "message", "Admin Dashboard",
            "stats", Map.of("totalUsers", 42, "totalAccounts", 100)
        ));
    }

    /**
     * Debit (subtract) amount from an account.
     * 
     * SECURITY: Internal endpoint only - called by transaction-service during transfers.
     * Protected by network policy in production (not exposed via Kong).
     * Path changed from /{accountId}/debit to /internal/{accountId}/debit (2024-12).
     */
    @PostMapping("/internal/{accountId}/debit")
    public ResponseEntity<?> debitAccount(
            @PathVariable String accountId,
            @RequestBody com.uit.accountservice.dto.request.AccountBalanceRequest request) {
        try {
            com.uit.accountservice.dto.response.AccountBalanceResponse response = 
                accountService.debitAccount(accountId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage(),
                "transactionId", request.getTransactionId()
            ));
        }
    }

    /**
     * Credit (add) amount to an account.
     */
    @PostMapping("/internal/{accountId}/credit")
    public ResponseEntity<?> creditAccount(
            @PathVariable String accountId,
            @RequestBody com.uit.accountservice.dto.request.AccountBalanceRequest request) {
        try {
            com.uit.accountservice.dto.response.AccountBalanceResponse response = 
                accountService.creditAccount(accountId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage(),
                "transactionId", request.getTransactionId()
            ));
        }
    }

    /**
     * Execute internal transfer between accounts.
     * 
     * SECURITY: Internal endpoint only - called by transaction-service.
     * Protected by network policy in production (not exposed via Kong).
     * Path changed from /internal-transfer to /internal/transfer (2024-12).
     */
    @PostMapping("/internal/transfer")
    public ResponseEntity<?> executeInternalTransfer(
            @RequestBody com.uit.accountservice.dto.request.InternalTransferRequest request) {
        try {
            com.uit.accountservice.dto.response.InternalTransferResponse response = 
                accountService.executeInternalTransfer(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage(),
                "transactionId", request.getTransactionId()
            ));
        }
    }

  // Section of BoLac
    private String getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName(); // Trả về 'sub' (userId)
    }

    

    /**
     * Get all accounts (Admin only).
     * Endpoint to retrieve a list of all accounts in the system.
     */
    @GetMapping("/all")
    @RequireRole("admin")
    public ResponseEntity<List<AccountDto>> getAllAccounts() {
        List<AccountDto> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }



    

    // GET /accounts
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDto>>> getMyAccounts() {
        List<AccountDto> accounts = accountService.getMyAccounts(getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    // GET /accounts/lookup?accountNumber=xxx&bankName=Stripe
    // Used to check if an account exists before initiating a transfer
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<AccountDto>> lookupAccountByAccountNumber(
            @RequestParam String accountNumber,
            @RequestParam(required = false, defaultValue = "Fortress Bank") String bankName) {
        AccountDto account = accountService.getAccountByAccountNumber(accountNumber, bankName);
        return ResponseEntity.ok(ApiResponse.success(account));
    }

   @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountDto>> getAccountDetail(@PathVariable("accountId") String accountId) {
        AccountDto account = accountService.getAccountDetail(accountId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(account));
    }

    @GetMapping("/internal/by-number/{accountNumber}")
    public ResponseEntity<AccountDto> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByAccountNumber(accountNumber, "Fortress Bank"));
    }

    // GET /accounts/{accountId}/balance
    @GetMapping("/balance/{accountId}")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getBalance(@PathVariable("accountId") String accountId) {
        BigDecimal balance = accountService.getBalance(accountId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("balance", balance)));
    }

    // POST /accounts
    @PostMapping()
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(
        @Valid @RequestBody CreateAccountRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        
        // Get full name from JWT claims - try 'name' first, then 'given_name'
        String fullName = jwt.getClaimAsString("name");
        if (fullName == null) {
            fullName = jwt.getClaimAsString("given_name");
        }
        
        AccountDto newAccount = accountService.createAccount(userId, request, fullName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(newAccount));
    }

    // DELETE /accounts/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable("id") String id) {
        accountService.closeAccount(id, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // POST /accounts/{id}/pin
    @PostMapping("/{id}/pin")
    public ResponseEntity<ApiResponse<Void>> createPin(@PathVariable("id") String id, @Valid @RequestBody PinRequest request) {
        accountService.createPin(id, getCurrentUserId(), request.newPin());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    // PUT /accounts/{id}/pin
    @PutMapping("/{id}/pin")
    public ResponseEntity<ApiResponse<Void>> updatePin(@PathVariable("id") String id, @Valid @RequestBody UpdatePinRequest request) {
        accountService.updatePin(id, getCurrentUserId(), request.oldPin(), request.newPin());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // POST /accounts/{accountId}/pin/verify
    @PostMapping("/{accountId}/pin/verify")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verifyPin(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody VerifyPinRequest request) {
        boolean isValid = accountService.verifyPin(accountId, getCurrentUserId(), request.pin());
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", isValid)));
    }

    // POST /accounts/transfers
    // Initiate transfer with ownership validation
    @PostMapping("/transfers")
    @RequireRole("user")
    public ResponseEntity<ApiResponse<Map<String, String>>> initiateTransfer(
            @Valid @RequestBody TransferRequest request) {
        // Validate ownership of sender account
        String userId = getCurrentUserId();
        accountService.initiateTransferWithOwnershipCheck(userId, request);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "message", "Transfer initiated successfully",
            "fromAccountId", request.getSenderAccountId(),
            "toAccountId", request.getReceiverAccountId()
        )));
    }

    // ==================== PUBLIC ENDPOINTS (LIMITED ACCESS) ====================

    /**
     * Public endpoint to create PIN without authentication.
     * Used after registration when user hasn't logged in yet.
     * 
     * SECURITY FIX (2024-12):
     * - Only allows PIN creation if account has NO existing PIN (first-time only)
     * - Prevents attackers from overwriting existing PINs
     * - TODO: Replace with time-limited registration token for full security
     */
    @PostMapping("/public/{accountId}/pin")
    public ResponseEntity<ApiResponse<Void>> createPinPublic(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody PinRequest request) {
        // Service layer validates this is first-time PIN creation only
        accountService.createPinPublic(accountId, request.newPin());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    // ==================== INTERNAL ENDPOINTS ====================

    /**
     * Internal endpoint for user-service to create account during registration
     * This endpoint is called by user-service via Feign Client
     */
    @PostMapping("/internal/create/{userId}")
    public ResponseEntity<ApiResponse<AccountDto>> createAccountForUser(
            @PathVariable("userId") String userId,
            @Valid @RequestBody CreateAccountRequest request,
            @RequestParam(value = "fullName", required = false) String fullName
    ) {
        AccountDto newAccount = accountService.createAccount(userId, request, fullName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(newAccount));
    }
}