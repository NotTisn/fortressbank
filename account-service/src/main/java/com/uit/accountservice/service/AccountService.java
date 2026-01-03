package com.uit.accountservice.service;

import com.uit.accountservice.client.UserClient;
import com.uit.accountservice.dto.AccountDto;
import com.uit.accountservice.dto.request.CreateAccountRequest;
import com.uit.accountservice.dto.request.SendSmsOtpRequest;
import com.uit.accountservice.dto.response.ChallengeResponse;
import com.uit.accountservice.dto.response.UserResponse;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.enums.AccountStatus;
import com.uit.accountservice.entity.enums.TransferStatus;
import com.uit.accountservice.mapper.AccountMapper;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.accountservice.riskengine.RiskEngineService;
import com.uit.accountservice.riskengine.dto.RiskAssessmentRequest;
import com.uit.accountservice.riskengine.dto.RiskAssessmentResponse;
import com.uit.sharedkernel.api.ApiResponse;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.sharedkernel.audit.AuditEventDto;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    // Hardcoded phone number for SMS OTPs - FOR DEVELOPMENT ONLY
    public static final String HARDCODED_PHONE_NUMBER = "+84857311444";

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final RiskEngineService riskEngineService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebClient.Builder webClientBuilder;
    private final TransferAuditService auditService; // Local DB audit
    private final AuditEventPublisher auditEventPublisher; // Centralized RabbitMQ audit
    private final PasswordEncoder passwordEncoder;
    private final UserClient userClient;
    private final CardService cardService;


    public List<AccountDto> getAccountsByUserId(String userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(accountMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll()
                .stream()
                .map(accountMapper::toDto)
                .collect(Collectors.toList());
    }

   
    /**
     * Debit (subtract) amount from an account.
     * Called by transaction-service for synchronous balance updates.
     * Uses pessimistic locking to prevent race conditions.
     */
    public AccountDto getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Account number not found: " + accountNumber));
        return accountMapper.toDto(account);
    }

    @Transactional
    public com.uit.accountservice.dto.response.AccountBalanceResponse debitAccount(
            String accountId, 
            com.uit.accountservice.dto.request.AccountBalanceRequest request) {
        
        log.info("Debiting account {} - Amount: {} - Transaction: {}", 
                accountId, request.getAmount(), request.getTransactionId());

        // Find account WITH PESSIMISTIC LOCK to prevent concurrent modifications
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND, 
                        "Account not found: " + accountId));

        BigDecimal oldBalance = account.getBalance();

        // Check sufficient balance (double-check after acquiring lock)
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance for account {} - Required: {} - Available: {}", 
                    accountId, request.getAmount(), account.getBalance());
            throw new AppException(ErrorCode.INSUFFICIENT_FUNDS, 
                    "Insufficient balance in account: " + accountId);
        }

        // Deduct amount atomically
        BigDecimal newBalance = account.getBalance().subtract(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Debit successful - Account: {} - Old balance: {} - New balance: {} - TxID: {}", 
                accountId, oldBalance, newBalance, request.getTransactionId());

        // Centralized Audit Log
        try {
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(accountId)
                    .action("ACCOUNT_DEBIT")
                    .userId(account.getUserId())
                    .oldValues(Map.of("balance", oldBalance.toString()))
                    .newValues(Map.of("balance", newBalance.toString()))
                    .changes("Account debited by " + request.getAmount())
                    .metadata(Map.of("transactionId", request.getTransactionId()))
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event for debit: {}", e.getMessage());
        }

        return com.uit.accountservice.dto.response.AccountBalanceResponse.builder()
                .accountId(accountId)
                .oldBalance(oldBalance)
                .newBalance(newBalance)
                .transactionId(request.getTransactionId())
                .success(true)
                .message("Debit successful")
                .build();
    }

    /**
     * Credit (add) amount to an account.
     * Called by transaction-service for synchronous balance updates.
     * Uses pessimistic locking to prevent race conditions.
     */
    @Transactional
    public com.uit.accountservice.dto.response.AccountBalanceResponse creditAccount(
            String accountId, 
            com.uit.accountservice.dto.request.AccountBalanceRequest request) {
        
        log.info("Crediting account {} - Amount: {} - Transaction: {}", 
                accountId, request.getAmount(), request.getTransactionId());

        // Find account WITH PESSIMISTIC LOCK to prevent concurrent modifications
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND, 
                        "Account not found: " + accountId));

        BigDecimal oldBalance = account.getBalance();

        // Add amount atomically
        BigDecimal newBalance = account.getBalance().add(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Credit successful - Account: {} - Old balance: {} - New balance: {} - TxID: {}", 
                accountId, oldBalance, newBalance, request.getTransactionId());

        // Centralized Audit Log
        try {
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(accountId)
                    .action("ACCOUNT_CREDIT")
                    .userId(account.getUserId())
                    .oldValues(Map.of("balance", oldBalance.toString()))
                    .newValues(Map.of("balance", newBalance.toString()))
                    .changes("Account credited by " + request.getAmount())
                    .metadata(Map.of("transactionId", request.getTransactionId()))
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event for credit: {}", e.getMessage());
        }

        return com.uit.accountservice.dto.response.AccountBalanceResponse.builder()
                .accountId(accountId)
                .oldBalance(oldBalance)
                .newBalance(newBalance)
                .transactionId(request.getTransactionId())
                .success(true)
                .message("Credit successful")
                .build();
    }

    /**
     * Execute internal transfer atomically in a single transaction.
     * Both debit and credit happen together - either both succeed or both fail.
     * Uses pessimistic locking on BOTH accounts to prevent race conditions.
     */
    @Transactional
    public com.uit.accountservice.dto.response.InternalTransferResponse executeInternalTransfer(
            com.uit.accountservice.dto.request.InternalTransferRequest request) {
        
        log.info("Executing internal transfer - From: {} To: {} Amount: {} TxID: {}", 
                request.getSenderAccountId(), request.getReceiverAccountId(), 
                request.getAmount(), request.getTransactionId());

        // Lock BOTH accounts in deterministic order to prevent deadlock
        List<String> accountIds = List.of(request.getSenderAccountId(), request.getReceiverAccountId());
        List<Account> accounts = accountRepository.findByIdInWithLock(accountIds);
        
        if (accounts.size() != 2) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, 
                    "One or both accounts not found");
        }
        
        // Identify sender and receiver
        Account fromAccount = accounts.stream()
                .filter(a -> a.getAccountId().equals(request.getSenderAccountId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND, 
                        "Sender account not found"));
        
        Account toAccount = accounts.stream()
                .filter(a -> a.getAccountId().equals(request.getReceiverAccountId()))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND, 
                        "Receiver account not found"));
        
        // Store old balances for response
        BigDecimal fromOldBalance = fromAccount.getBalance();
        BigDecimal toOldBalance = toAccount.getBalance();
        
        // Check sufficient balance
        if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient balance - Account: {} Required: {} Available: {}", 
                    request.getSenderAccountId(), request.getAmount(), fromAccount.getBalance());
            throw new AppException(ErrorCode.INSUFFICIENT_FUNDS, 
                    "Insufficient balance in sender account");
        }
        
        // Execute transfer atomically
        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

        // Save both accounts (within same transaction)
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        log.info("Internal transfer completed - TxID: {} - Sender: {} ({} → {}) - Receiver: {} ({} → {})",
                request.getTransactionId(),
                request.getSenderAccountId(), fromOldBalance, fromAccount.getBalance(),
                request.getReceiverAccountId(), toOldBalance, toAccount.getBalance());

        // Centralized Audit Log (Sender)
        try {
            AuditEventDto senderAudit = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(request.getSenderAccountId())
                    .action("INTERNAL_TRANSFER_SENT")
                    .userId(fromAccount.getUserId())
                    .oldValues(Map.of("balance", fromOldBalance.toString()))
                    .newValues(Map.of("balance", fromAccount.getBalance().toString()))
                    .changes("Transferred " + request.getAmount() + " to " + request.getReceiverAccountId())
                    .metadata(Map.of(
                        "transactionId", request.getTransactionId(),
                        "receiverAccountId", request.getReceiverAccountId()
                    ))
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(senderAudit);

            // Centralized Audit Log (Receiver)
            AuditEventDto receiverAudit = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(request.getReceiverAccountId())
                    .action("INTERNAL_TRANSFER_RECEIVED")
                    .userId(toAccount.getUserId())
                    .oldValues(Map.of("balance", toOldBalance.toString()))
                    .newValues(Map.of("balance", toAccount.getBalance().toString()))
                    .changes("Received " + request.getAmount() + " from " + request.getSenderAccountId())
                    .metadata(Map.of(
                        "transactionId", request.getTransactionId(),
                        "senderAccountId", request.getSenderAccountId()
                    ))
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(receiverAudit);
        } catch (Exception e) {
            log.error("Failed to publish audit event for internal transfer: {}", e.getMessage());
        }

        return com.uit.accountservice.dto.response.InternalTransferResponse.builder()
                .transactionId(request.getTransactionId())
                .senderAccountId(request.getSenderAccountId())
                .senderAccountOldBalance(fromOldBalance)
                .senderAccountNewBalance(fromAccount.getBalance())
                .receiverAccountId(request.getReceiverAccountId())
                .receiverAccountOldBalance(toOldBalance)
                .receiverAccountNewBalance(toAccount.getBalance())
                .amount(request.getAmount())
                .success(true)
                .message("Internal transfer completed successfully")
                .build();
    }

    // SECTION BOLAC <3
    public List<AccountDto> getMyAccounts(String userId) {
        // Chỉ lấy các tài khoản chưa bị đóng
        return accountRepository.findByUserId(userId).stream()
                .filter(a -> a.getStatus() != AccountStatus.CLOSED)
                .map(accountMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get account by account number (for lookup during transfer)
     * This endpoint is used to check if an account exists before initiating a transfer
     * Supports both internal (Fortress Bank) and external (Stripe) lookups.
     * 
     * @param accountNumber The account number to lookup
     * @param bankName The name of the bank (optional, defaults to "Fortress Bank")
     * @return AccountDto with account information
     */
    public AccountDto getAccountByAccountNumber(String accountNumber, String bankName) {
        // External Lookup: Stripe
        if ("Stripe".equalsIgnoreCase(bankName)) {
            log.info("Performing external lookup on Stripe for account: {}", accountNumber);
            try {
                // Synchronous call to Stripe API - Fast enough for direct lookup
                com.stripe.model.Account stripeAccount = com.stripe.model.Account.retrieve(accountNumber);
                
                if (stripeAccount.getDeleted() != null && stripeAccount.getDeleted()) {
                    throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Stripe account is deleted: " + accountNumber);
                }

                // Map Stripe account to AccountDto
                String displayName = "Stripe User";
                if (stripeAccount.getBusinessProfile() != null && stripeAccount.getBusinessProfile().getName() != null) {
                    displayName = stripeAccount.getBusinessProfile().getName();
                } else if (stripeAccount.getEmail() != null) {
                    displayName = stripeAccount.getEmail();
                }

                return AccountDto.builder()
                        .accountId(stripeAccount.getId()) // Use Stripe ID as Account ID
                        .accountNumber(stripeAccount.getId())
                        .fullName(displayName)
                        .accountStatus(AccountStatus.ACTIVE.name()) 
                        .build();

            } catch (com.stripe.exception.StripeException e) {
                log.error("Stripe lookup failed: {}", e.getMessage());
                throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, 
                    "Stripe account validation failed: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error during Stripe lookup", e);
                throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION, "External lookup error");
            }
        }

        // Internal Lookup: Fortress Bank
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found with account number: " + accountNumber));

        // Only return active accounts for transfers
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AppException(ErrorCode.ACCOUNT_STATUS_CONFLICT,
                "This account has been closed and cannot receive transfers");
        }

        AccountDto accountDto = accountMapper.toDto(account);

        try {
            ApiResponse<UserResponse> userResponse = userClient.getUserById(account.getUserId());
            
            if (userResponse != null && userResponse.getData() != null) {
                accountDto.setFullName(userResponse.getData().fullName());
            } else {
                accountDto.setFullName("Unknown User");
            }
        } catch (Exception e) {
            log.error("Failed to fetch user name for account lookup: {}", e.getMessage());
            accountDto.setFullName("Unknown User");
        }

        return accountDto;
    }

    public AccountDto getAccountDetail(String accountId, String userId) {
        Account account = getAccountOwnedByUser(accountId, userId);
        return accountMapper.toDto(account);
    }

    public BigDecimal getBalance(String accountId, String userId) {
        Account account = getAccountOwnedByUser(accountId, userId);
        return account.getBalance();
    }

    @Transactional
    public AccountDto createAccount(String userId, CreateAccountRequest request, String fullName) {
        String accountNumber;

        // Determine account number based on accountNumberType
        if ("PHONE_NUMBER".equals(request.accountNumberType())) {
            // Use phone number from request if provided, otherwise fetch from user-service
            String phoneNumber;
            if (request.phoneNumber() != null && !request.phoneNumber().isEmpty()) {
                phoneNumber = request.phoneNumber();
            } else {
                phoneNumber = fetchPhoneNumberFromUserService(userId);
                if (phoneNumber == null || phoneNumber.isEmpty()) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                        "User does not have a phone number registered. Please update your profile first or use AUTO_GENERATE.");
                }
            }

            // Convert international format (+84...) to Vietnamese local format (0...)
            // Example: +84857311444 → 0857311444
            if (phoneNumber.startsWith("+84")) {
                accountNumber = "0" + phoneNumber.substring(3);
            } else if (phoneNumber.startsWith("84")) {
                accountNumber = "0" + phoneNumber.substring(2);
            } else if (phoneNumber.startsWith("0")) {
                accountNumber = phoneNumber; // Already in correct format
            } else {
                // Fallback: use as-is but log warning
                accountNumber = phoneNumber;
                log.warn("Phone number format unexpected: {}. Using as-is for account number.", phoneNumber);
            }

            log.info("Phone-based account number: {} (from phone: {})", accountNumber, phoneNumber);

            // Check if account with this phone number already exists (globally)
            if (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                    "An account with your phone number already exists. Each phone number can only be used once.");
            }

        } else if ("AUTO_GENERATE".equals(request.accountNumberType())) {
            // Generate unique account number
            accountNumber = generateUniqueAccountNumber();

        } else {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid accountNumberType: " + request.accountNumberType());
        }

        // Additional check: User cannot create duplicate accounts with same account number
        boolean userAlreadyHasThisAccountNumber = accountRepository.findByUserId(userId).stream()
                .anyMatch(account -> account.getAccountNumber().equals(accountNumber));

        if (userAlreadyHasThisAccountNumber) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                "You already have an account with this account number: " + accountNumber);
        }

        // Validate and hash PIN if provided
        String pinHash = null;
        if (request.pin() != null && !request.pin().isEmpty()) {
            validatePinFormat(request.pin());
            pinHash = passwordEncoder.encode(request.pin());
            log.info("PIN set for new account with number {}", accountNumber);
        }

        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .pinHash(pinHash)
                .build();

        log.info("Account created successfully - UserId: {} - AccountNumber: {}", userId, accountNumber);

        Account savedAccount = accountRepository.save(account);

        try {
            if (fullName == null || fullName.trim().isEmpty()) {
                try {
                    ApiResponse<UserResponse> userRes = userClient.getUserById(userId);
                    if (userRes != null && userRes.getData() != null) {
                        fullName = userRes.getData().fullName();
                    }
                } catch (Exception ex) {
                    log.warn("Could not fetch user name for card creation via Feign: {}", ex.getMessage());
                }
            }
            
            cardService.createInitialCard(savedAccount, fullName);
            
        } catch (Exception e) {
            log.error("Failed to auto-create card for account {}: {}", savedAccount.getAccountId(), e.getMessage());
        }

        // Centralized Audit Log
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("account-service")
                .entityType("Account")
                .entityId(savedAccount.getAccountId())
                .action("CREATE_ACCOUNT")
                .userId(userId)
                .newValues(Map.of(
                    "accountNumber", accountNumber,
                    "status", AccountStatus.ACTIVE.toString(),
                    "currency", "VND", // Assuming VND default
                    "accountNumberType", request.accountNumberType()
                ))
                .changes("New account opened")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);


        return accountMapper.toDto(savedAccount);
    }

    /**
     * Fetch phone number from user-service for the given userId
     * @param userId The user ID
     * @return Phone number or null if not found
     */
    private String fetchPhoneNumberFromUserService(String userId) {
        try {
            ApiResponse<UserResponse> response = userClient.getUserById(userId);
            if (response != null && response.getData() != null && response.getData().phoneNumber() != null) {
                return response.getData().phoneNumber();
            }
        } catch (Exception e) {
            log.error("Failed to fetch user phone number from user-service for userId: {}. Error: {}",
                    userId, e.getMessage());
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION,
                "Failed to retrieve user information. Please try again later.");
        }
        return null;
    }

    @Transactional
    public void closeAccount(String accountId, String userId) {
        Account account = getAccountOwnedByUser(accountId, userId);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AppException(ErrorCode.ACCOUNT_STATUS_CONFLICT, "Account is already closed");
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new AppException(ErrorCode.ACCOUNT_CLOSE_NONZERO_BALANCE);
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);

        // Centralized Audit Log
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("account-service")
                .entityType("Account")
                .entityId(accountId)
                .action("CLOSE_ACCOUNT")
                .userId(userId)
                .newValues(Map.of("status", AccountStatus.CLOSED.toString()))
                .changes("Account closed by user")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);
    }

    @Transactional
    public void createPin(String accountId, String userId, String newPin) {
        validatePinFormat(newPin);
        Account account = getAccountOwnedByUser(accountId, userId);
        if (account.getPinHash() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PIN already exists. Use PUT to update.");
        }
        account.setPinHash(passwordEncoder.encode(newPin));
        accountRepository.save(account);
    }

    @Transactional
    public void updatePin(String accountId, String userId, String oldPin, String newPin) {
        validatePinFormat(newPin);
        Account account = getAccountOwnedByUser(accountId, userId);

        if (account.getPinHash() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PIN not set. Use POST to create.");
        }

        if (!passwordEncoder.matches(oldPin, account.getPinHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS, "Old PIN is incorrect");
        }

        account.setPinHash(passwordEncoder.encode(newPin));
        accountRepository.save(account);
    }

    /**
     * Create PIN without authentication (for post-registration flow).
     * This allows users to set up PIN immediately after registration without logging in.
     */
    @Transactional
    public void createPinPublic(String accountId, String newPin) {
        validatePinFormat(newPin);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getPinHash() != null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PIN already exists. Cannot create duplicate PIN.");
        }

        account.setPinHash(passwordEncoder.encode(newPin));
        accountRepository.save(account);
        log.info("PIN created successfully for account {}", accountId);
    }

    /**
     * Verify if the provided PIN matches the account's PIN.
     * Used during transfer flow to validate user authorization.
     */
    public boolean verifyPin(String accountId, String userId, String pin) {
        validatePinFormat(pin);
        Account account = getAccountOwnedByUser(accountId, userId);

        if (account.getPinHash() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PIN not set for this account");
        }

        return passwordEncoder.matches(pin, account.getPinHash());
    }

    // Helpers
    private Account getAccountOwnedByUser(String accountId, String userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (!account.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Access denied");
        }
        return account;
    }

    private String generateUniqueAccountNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        // Số đầu tiên từ 1-9 để tránh số 0 ở đầu
        sb.append(random.nextInt(9) + 1);
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }

        String accNum = sb.toString();

        // Đệ quy check trùng (tuy xác suất thấp nhưng cần thiết cho banking)
        if (accountRepository.existsByAccountNumber(accNum)) {
            return generateUniqueAccountNumber();
        }
        return accNum;
    }

    private void validatePinFormat(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new AppException(ErrorCode.BAD_REQUEST, "PIN must be exactly 6 digits");
        }
    }

    // ==================== ADMIN OPERATIONS ====================

    /**
     * Get all accounts with pagination (Admin only).
     * Returns paginated list of all accounts in the system.
     *
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of AccountDto with user information
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AccountDto> getAllAccountsPaged(org.springframework.data.domain.Pageable pageable) {
        log.info("Admin fetching all accounts - Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());

        org.springframework.data.domain.Page<Account> accountsPage = accountRepository.findAll(pageable);

        // Map to DTO and fetch user information for each account
        return accountsPage.map(account -> {
            AccountDto dto = accountMapper.toDto(account);

            // Fetch user's full name from user-service
            try {
                ApiResponse<UserResponse> userResponse = userClient.getUserById(account.getUserId());
                if (userResponse != null && userResponse.getData() != null) {
                    dto.setFullName(userResponse.getData().fullName());
                } else {
                    dto.setFullName("Unknown User");
                }
            } catch (Exception e) {
                log.error("Failed to fetch user info for userId: {} - {}", account.getUserId(), e.getMessage());
                dto.setFullName("Unknown User");
            }

            return dto;
        });
    }

    /**
     * Get account by ID (Admin only).
     * No ownership check - admin can view any account.
     *
     * @param accountId Account ID to retrieve
     * @return AccountDto with user information
     */
    @Transactional(readOnly = true)
    public AccountDto getAccountById(String accountId) {
        log.info("Admin fetching account by ID: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found with ID: " + accountId));

        AccountDto dto = accountMapper.toDto(account);

        // Fetch user's full name
        try {
            ApiResponse<UserResponse> userResponse = userClient.getUserById(account.getUserId());
            if (userResponse != null && userResponse.getData() != null) {
                dto.setFullName(userResponse.getData().fullName());
            } else {
                dto.setFullName("Unknown User");
            }
        } catch (Exception e) {
            log.error("Failed to fetch user info for userId: {} - {}", account.getUserId(), e.getMessage());
            dto.setFullName("Unknown User");
        }

        return dto;
    }

    /**
     * Update account (Admin only).
     * Allows admin to update account status.
     *
     * @param accountId Account ID to update
     * @param request Update request containing fields to update
     * @return Updated AccountDto
     */
    @Transactional
    public AccountDto updateAccount(String accountId, com.uit.accountservice.dto.request.UpdateAccountRequest request) {
        log.info("Admin updating account: {} - New status: {}", accountId, request.status());

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found with ID: " + accountId));

        String oldStatus = account.getStatus() != null ? account.getStatus().name() : null;

        // Update status if provided
        if (request.status() != null && !request.status().isEmpty()) {
            try {
                AccountStatus newStatus = AccountStatus.valueOf(request.status());

                // Business rule: Cannot close account with non-zero balance
                if (newStatus == AccountStatus.CLOSED &&
                    account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    throw new AppException(ErrorCode.ACCOUNT_CLOSE_NONZERO_BALANCE,
                        "Cannot close account with non-zero balance. Current balance: " + account.getBalance());
                }

                account.setStatus(newStatus);
                log.info("Account {} status changed from {} to {}", accountId, oldStatus, newStatus);

            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                    "Invalid account status: " + request.status());
            }
        }

        Account updatedAccount = accountRepository.save(account);

        // Centralized Audit Log
        try {
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(accountId)
                    .action("UPDATE_ACCOUNT")
                    .userId("ADMIN")
                    .oldValues(Map.of("status", oldStatus != null ? oldStatus : "N/A"))
                    .newValues(Map.of("status", updatedAccount.getStatus().name()))
                    .changes("Admin updated account status")
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event for account update: {}", e.getMessage());
        }

        AccountDto dto = accountMapper.toDto(updatedAccount);

        // Fetch user's full name
        try {
            ApiResponse<UserResponse> userResponse = userClient.getUserById(account.getUserId());
            if (userResponse != null && userResponse.getData() != null) {
                dto.setFullName(userResponse.getData().fullName());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user info: {}", e.getMessage());
        }

        return dto;
    }

    /**
     * Lock account (Admin only).
     * Convenience method to lock an account.
     *
     * @param accountId Account ID to lock
     * @return Updated AccountDto
     */
    @Transactional
    public AccountDto lockAccount(String accountId) {
        log.info("Admin locking account: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found with ID: " + accountId));

        // Check current status
        if (account.getStatus() == AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_STATUS_CONFLICT,
                "Account is already locked");
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AppException(ErrorCode.ACCOUNT_STATUS_CONFLICT,
                "Cannot lock a closed account");
        }

        String oldStatus = account.getStatus().name();
        account.setStatus(AccountStatus.LOCKED);
        Account updatedAccount = accountRepository.save(account);

        log.info("Account {} locked successfully. Previous status: {}", accountId, oldStatus);

        // Centralized Audit Log
        try {
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(accountId)
                    .action("LOCK_ACCOUNT")
                    .userId("ADMIN")
                    .oldValues(Map.of("status", oldStatus))
                    .newValues(Map.of("status", AccountStatus.LOCKED.name()))
                    .changes("Admin locked account")
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event for account lock: {}", e.getMessage());
        }

        AccountDto dto = accountMapper.toDto(updatedAccount);

        // Fetch user's full name
        try {
            ApiResponse<UserResponse> userResponse = userClient.getUserById(account.getUserId());
            if (userResponse != null && userResponse.getData() != null) {
                dto.setFullName(userResponse.getData().fullName());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user info: {}", e.getMessage());
        }

        return dto;
    }

    /**
     * Unlock account (Admin only).
     * Convenience method to unlock a locked account.
     *
     * @param accountId Account ID to unlock
     * @return Updated AccountDto
     */
    @Transactional
    public AccountDto unlockAccount(String accountId) {
        log.info("Admin unlocking account: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND,
                    "Account not found with ID: " + accountId));

        // Check current status
        if (account.getStatus() != AccountStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_STATUS_CONFLICT,
                "Only locked accounts can be unlocked. Current status: " + account.getStatus());
        }

        account.setStatus(AccountStatus.ACTIVE);
        Account updatedAccount = accountRepository.save(account);

        log.info("Account {} unlocked successfully", accountId);

        // Centralized Audit Log
        try {
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(accountId)
                    .action("UNLOCK_ACCOUNT")
                    .userId("ADMIN")
                    .oldValues(Map.of("status", AccountStatus.LOCKED.name()))
                    .newValues(Map.of("status", AccountStatus.ACTIVE.name()))
                    .changes("Admin unlocked account")
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);
        } catch (Exception e) {
            log.error("Failed to publish audit event for account unlock: {}", e.getMessage());
        }

        AccountDto dto = accountMapper.toDto(updatedAccount);

        // Fetch user's full name
        try {
            ApiResponse<UserResponse> userResponse = userClient.getUserById(account.getUserId());
            if (userResponse != null && userResponse.getData() != null) {
                dto.setFullName(userResponse.getData().fullName());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user info: {}", e.getMessage());
        }

        return dto;
    }

    // ==================== ADMIN OPERATIONS ====================

    /**
     * Admin creates account for a user.
     * This method wraps createAccount() with additional admin-specific audit logging.
     * 
     * All validations and features from createAccount() are applied:
     * - Phone number uniqueness: Only ONE account can use each phone number globally
     * - User cannot have duplicate account numbers  
     * - Automatic card creation for the new account (if fullName provided)
     * - PIN encoding and storage
     * 
     * @param userId The user ID for whom the account is being created
     * @param request CreateAccountRequest with accountNumberType, phoneNumber, and PIN
     * @param fullName User's full name for card creation (fetched from user-service in controller)
     * @return AccountDto of the created account
     */
    @Transactional
    public AccountDto adminCreateAccount(String userId, CreateAccountRequest request, String fullName) {
        log.info("[ADMIN] Creating account for userId: {} - Type: {} - FullName: {}", 
                userId, request.accountNumberType(), fullName);

        // Reuse existing createAccount logic which includes:
        // 1. Phone number validation (only 1 account per phone globally)
        // 2. Account number uniqueness check for this user
        // 3. Fetch fullName from user-service if not provided
        // 4. Automatic card creation via cardService.createInitialCard()
        // 5. PIN encoding and storage
        AccountDto createdAccount = createAccount(userId, request, fullName);

        // Additional admin-specific audit log to track admin actions
        try {
            AuditEventDto adminAudit = AuditEventDto.builder()
                    .serviceName("account-service")
                    .entityType("Account")
                    .entityId(createdAccount.getAccountId())
                    .action("ADMIN_CREATE_ACCOUNT")
                    .userId("ADMIN") // Mark this as admin action
                    .newValues(Map.of(
                        "targetUserId", userId,
                        "accountNumber", createdAccount.getAccountNumber(),
                        "accountNumberType", request.accountNumberType(),
                        "hasPin", request.pin() != null ? "YES" : "NO"
                    ))
                    .changes("Admin created account for user: " + userId)
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(adminAudit);
        } catch (Exception e) {
            log.error("Failed to publish admin audit event: {}", e.getMessage());
        }

        log.info("[ADMIN] Successfully created account {} for user {}", 
                createdAccount.getAccountId(), userId);
        return createdAccount;
    }

    /**
     * Admin updates PIN for an account without requiring old PIN
     */
    @Transactional
    public void adminUpdatePin(String accountId, String newPin) {
        validatePinFormat(newPin);
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        // Admin can set/update PIN without old PIN verification
        account.setPinHash(passwordEncoder.encode(newPin));
        accountRepository.save(account);
        
        log.info("Admin updated PIN for account: {}", accountId);
    }

}