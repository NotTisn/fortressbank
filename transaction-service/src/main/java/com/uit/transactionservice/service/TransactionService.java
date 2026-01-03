package com.uit.transactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uit.sharedkernel.outbox.OutboxEvent;
import com.uit.sharedkernel.outbox.OutboxEventStatus;
import com.uit.sharedkernel.outbox.repository.OutboxEventRepository;
import com.uit.sharedkernel.constants.RabbitMQConstants;
import com.uit.sharedkernel.exception.AppException;
import com.uit.sharedkernel.exception.ErrorCode;
import com.uit.sharedkernel.notification.NotificationEventPublisher;
import com.uit.transactionservice.client.AccountServiceClient;
import com.uit.transactionservice.client.dto.AccountBalanceResponse;
import com.uit.transactionservice.client.dto.InternalTransferResponse;
import com.uit.transactionservice.dto.request.CreateTransferRequest;
import com.uit.transactionservice.dto.response.TransactionResponse;
import com.uit.transactionservice.entity.*;
import com.uit.transactionservice.exception.AccountServiceException;
import com.uit.transactionservice.exception.InsufficientBalanceException;
import com.uit.transactionservice.mapper.TransactionMapper;
import com.uit.transactionservice.repository.TransactionFeeRepository;
import com.uit.transactionservice.repository.TransactionLimitRepository;
import com.uit.transactionservice.repository.TransactionRepository;
import com.uit.transactionservice.dto.stripe.StripeTransferRequest;
import com.uit.transactionservice.dto.stripe.StripeTransferResponse;
import com.uit.transactionservice.dto.SepayWebhookDto;
import com.uit.transactionservice.dto.sse.TransactionStatusUpdate;
import com.uit.sharedkernel.audit.AuditEventDto;
import com.uit.sharedkernel.audit.AuditEventPublisher;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.uit.sharedkernel.dto.*;
import com.uit.transactionservice.dto.request.AdminDepositRequest;
import com.uit.transactionservice.dto.request.ConfirmFaceAuthRequest;
import com.uit.transactionservice.client.dto.RiskAssessmentRequest;
import com.uit.transactionservice.client.dto.RiskAssessmentResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionFeeRepository transactionFeeRepository;
    private final TransactionLimitRepository transactionLimitRepository;
    private final TransactionMapper transactionMapper;
    private final ObjectMapper objectMapper;
    private final OTPService otpService;
    private final AccountServiceClient accountServiceClient;
    private final StripeTransferService stripeTransferService;
    private final TransactionSseService sseService;
    private final AuditEventPublisher auditEventPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final com.uit.transactionservice.client.RiskEngineClient riskEngineClient;
    
    /**
     * Handle SePay webhook for Top-up (Deposit)
     */
    @Transactional
    public void handleSepayTopup(SepayWebhookDto webhookData, String accountNumber) {
        log.info("Processing SePay Top-up - AccountID: {} - Amount: {} - SePay Ref: {}", 
                accountNumber, webhookData.getTransferAmount(), webhookData.getCode());

        // if (transactionRepository.existsByExternalTransactionId(webhookData.getCode())) {
        //     log.warn("SePay transaction already processed - Ref: {}", webhookData.getCode());
        //     return;
        // }
       String receiverAccountId = null;
       String receiverUserId = null;
        try {
            Map<String, Object> receiverAccount = accountServiceClient.getAccountByNumber(accountNumber);
            if (receiverAccount != null) {
                receiverAccountId = (String) receiverAccount.get("accountId");
                receiverUserId = (String) receiverAccount.get("userId");
            } else {
                throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Account number not found: " + accountNumber    );
            }
        } catch (Exception e) {
            log.error("Failed to resolve account number: {}", accountNumber, e);
            throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Account validation failed");
        }
        String correlationId = UUID.randomUUID().toString();

        Transaction transaction = Transaction.builder()
                .senderAccountNumber("SEPAY_GATEWAY") // Virtual sender
                .receiverAccountNumber(accountNumber)
                .senderUserId(null)
                .receiverUserId(receiverUserId)
                .amount(webhookData.getTransferAmount())
                .feeAmount(BigDecimal.ZERO)
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .description(webhookData.getDescription())
                .externalTransactionId(webhookData.getCode()) // Store SePay ref
                .correlationId(correlationId)
                .currentStep(SagaStep.STARTED)
                .createdAt(LocalDateTime.now())
                .build();
        
        transaction = transactionRepository.save(transaction);
        log.info("Created Deposit Transaction - TxID: {}", transaction.getTransactionId());

        try {
            log.info("Crediting account {} with amount {}", accountNumber, webhookData.getTransferAmount());
            
            accountServiceClient.creditAccount(
                    receiverAccountId,
                    webhookData.getTransferAmount(),
                    transaction.getTransactionId().toString(),
                    "SePay Deposit: " + webhookData.getDescription()
            );

            // Audit Log 1: Account Balance Updated
            auditEventPublisher.publishAuditEvent(AuditEventDto.builder()
                    .serviceName("account-service") // Route to AccountAuditLog
                    .entityType("Account")
                    .entityId(receiverAccountId)
                    .action("ACCOUNT_BALANCE_UPDATED_SEPAY")
                    .userId(null)
                    .newValues(Map.of(
                        "transactionId", transaction.getTransactionId().toString(),
                        "amount", webhookData.getTransferAmount(),
                        "reason", "SePay Deposit"
                    ))
                    .changes("Account balance credited via SePay")
                    .result("SUCCESS")
                    .build());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCurrentStep(SagaStep.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            
            log.info("SePay Deposit Completed Successfully - TxID: {}", transaction.getTransactionId());
            
            sendTransactionNotification(transaction, "DepositCompleted", true,2);

            // Audit Log 2: Transaction Completed
            auditEventPublisher.publishAuditEvent(AuditEventDto.builder()
                    .serviceName("transaction-service")
                    .entityType("Transaction")
                    .entityId(transaction.getTransactionId().toString())
                    .action("SEPAY_TRANSACTION_COMPLETED")
                    .userId(null)
                    .newValues(Map.of(
                        "receiverAccountId", receiverAccountId,
                        "amount", webhookData.getTransferAmount(),
                        "externalTransactionId", webhookData.getCode(),
                        "status", TransactionStatus.COMPLETED.toString()
                    ))
                    .changes("SePay Deposit transaction completed")
                    .result("SUCCESS")
                    .build());

        } catch (Exception e) {
            log.error("Failed to credit account for SePay Deposit - TxID: {}", transaction.getTransactionId(), e);
            
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCurrentStep(SagaStep.FAILED);
            transaction.setFailureReason("Failed to credit account: " + e.getMessage());
            transactionRepository.save(transaction);
            
            sendTransactionNotification(transaction, "DepositFailed", false,2);
            throw new RuntimeException("Failed to process SePay deposit", e);
        }
    }


    /**
     * Create Admin Deposit (Manual Top-up)
     * This creates a transaction record first, then credits the account.
     */
    @Transactional
    public TransactionResponse createAdminDeposit(AdminDepositRequest request) {
        log.info("Processing Admin Deposit - Account: {} - Amount: {}", request.getAccountNumber(), request.getAmount());

        // 1. Resolve Account ID from Account Number
        String accountId = null;
        String userId = null;
  
        try {
            Map<String, Object> accountInfo = accountServiceClient.getAccountByNumber(request.getAccountNumber());
            if (accountInfo != null) {
                accountId = (String) accountInfo.get("accountId");
                userId = (String) accountInfo.get("userId");
            } else {
                throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Account number not found: " + request.getAccountNumber());
            }
        } catch (Exception e) {
            log.error("Failed to resolve account number: {}", request.getAccountNumber(), e);
            throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Account validation failed");
        }

        // 2. Create Transaction Record (DEPOSIT)
        String correlationId = UUID.randomUUID().toString();
        Transaction transaction = Transaction.builder()
                .senderAccountNumber("ADMIN_DEPOSIT") // Virtual sender
                .receiverAccountId(accountId)
                .receiverAccountNumber(request.getAccountNumber())
                .receiverUserId(userId)
                .senderAccountId("admin")
                .senderUserId("admin")
                .amount(request.getAmount())
                .feeAmount(BigDecimal.ZERO)
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .description(request.getDescription() != null ? request.getDescription() : "Admin Deposit")
                .correlationId(correlationId)
                .currentStep(SagaStep.STARTED)
                .createdAt(LocalDateTime.now())
                .build();
        
        transaction = transactionRepository.save(transaction);
        log.info("Created Admin Deposit Transaction - TxID: {}", transaction.getTransactionId());

        sendTransactionNotification(transaction, "DepositInitiated", true,2);
        // 3. Credit Account via Account Service
        try {
            accountServiceClient.creditAccount(
                    accountId,
                    request.getAmount(),
                    transaction.getTransactionId().toString(),
                    "Admin Deposit: " + transaction.getDescription()
            );

            // 4. Update Transaction Status
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCurrentStep(SagaStep.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            
            // 5. Send Notification & Audit
            sendTransactionNotification(transaction, "DepositCompleted", true,2);

            auditEventPublisher.publishAuditEvent(AuditEventDto.builder()
                    .serviceName("transaction-service")
                    .entityType("Transaction")
                    .entityId(transaction.getTransactionId().toString())
                    .action("ADMIN_DEPOSIT_COMPLETED")
                    .userId("ADMIN") // Ideally get actual admin ID from context
                    .newValues(Map.of(
                        "receiverAccountId", accountId,
                        "amount", request.getAmount(),
                        "status", TransactionStatus.COMPLETED.toString()
                    ))
                    .changes("Admin deposit completed")
                    .result("SUCCESS")
                    .build());

            return transactionMapper.toResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to process admin deposit - TxID: {}", transaction.getTransactionId(), e);
            
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason("Failed to credit account: " + e.getMessage());
            transactionRepository.save(transaction);
            
            throw new RuntimeException("Failed to process admin deposit", e);
        }
    }

    @Transactional
    public TransactionResponse createTransfer(CreateTransferRequest request, String userId, String phoneNumber) {
        log.info("Creating transfer from {} to {} with OTP", request.getSenderAccountNumber(), request.getReceiverAccountNumber());

        String senderUserId = null;

        // 1. Validate Sender Account Exists (Always)
        try {
            Map<String, Object> senderInfo = accountServiceClient.getAccountByNumber(request.getSenderAccountNumber());
            if (senderInfo == null) {
                 throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Sender account not found: " + request.getSenderAccountNumber());
            }

            Object senderUserIdObj = senderInfo.get("userId");
            if (senderUserIdObj != null) {
                senderUserId = senderUserIdObj.toString();
                log.info("Sender account validated - AccountNumber: {}, UserId: {}",
                    request.getSenderAccountNumber(), senderUserId);
            } else {
                log.warn("Sender account has no userId: {}", request.getSenderAccountNumber());
            }
        } catch (Exception e) {
             log.error("Failed to validate sender account: {}", e.getMessage());
             throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Sender account validation failed");
        }


        // 2. Validate Receiver based on Transaction Type
        String receiverAccountNumber = request.getReceiverAccountNumber();
        String receiverAccountId = null;
        String receiverUserId = null;

        if (request.getTransactionType() == TransactionType.INTERNAL_TRANSFER) {
            // INTERNAL: Check against local Account Service
            log.info("Validating INTERNAL receiver - AccountNumber: {}", receiverAccountNumber);
            try {
                Map<String, Object> accountMap = accountServiceClient.getAccountByNumber(receiverAccountNumber);
                if (accountMap != null) {
                    receiverAccountId = (String) accountMap.get("accountId");
                    Object rUserId = accountMap.get("userId");
                    if (rUserId != null) {
                        receiverUserId = rUserId.toString();
                    }
                    log.info("Receiver found locally - ID: {}, UserID: {}", receiverAccountId, receiverUserId);
                } else {
                    throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Receiver account not found for internal transfer");
                }
            } catch (Exception e) {
                log.warn("Failed to check local DB for receiver: {}", e.getMessage());
                throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "Receiver account not found: " + e.getMessage());
            }
        
        } else if (request.getTransactionType() == TransactionType.EXTERNAL_TRANSFER) {
            // EXTERNAL: Validate via Stripe, do NOT check local Account Service
            log.info("Validating EXTERNAL receiver via Stripe - Account: {}", receiverAccountNumber);
            try {
                boolean isValidInStripe = stripeTransferService.validateConnectedAccount(receiverAccountNumber);
                if (!isValidInStripe) {
                    log.error("Receiver account invalid in Stripe - Account: {}", receiverAccountNumber);
                    throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND, "External receiver account invalid");
                }
                log.info("Receiver account validated in Stripe");
                
            } catch (com.stripe.exception.StripeException e) {
                log.error("Failed to validate receiver account with Stripe: {}", e.getMessage());
                throw new RuntimeException("Failed to validate external receiver account", e);
            }
        }

        // 3. Check transaction limit using Sender Account ID
        checkTransactionLimit(request.getSenderAccountId(), request.getAmount());

        // 4. Perform Risk Assessment
        log.info("Performing risk assessment for User: {} - Amount: {}", userId, request.getAmount());
        boolean requireFaceAuth = false;
        try {
            RiskAssessmentRequest riskRequest = RiskAssessmentRequest.builder()
                    .userId(userId)
                    .amount(request.getAmount())
                    .payeeId(receiverAccountNumber)
                    .build();
            
            RiskAssessmentResponse riskResponse = riskEngineClient.assessRisk(riskRequest);
            
            log.info("Risk Level: {} - Challenge: {}", riskResponse.getRiskLevel(), riskResponse.getChallengeType());
            
            if ("HIGH".equals(riskResponse.getRiskLevel())) {
                requireFaceAuth = true;
                log.warn("High risk detected! Transaction {} will require FaceID authentication.", request.getSenderAccountNumber());
            }
        } catch (Exception e) {
            log.error("Risk assessment failed, falling back to standard OTP: {}", e.getMessage());
        }

        // 5. Fee is temporarily set to ZERO (no transaction fee for now)
        BigDecimal fee = BigDecimal.ZERO;

        // 6. Create transaction
        String correlationId = UUID.randomUUID().toString();
        
        TransactionStatus initialStatus = requireFaceAuth ? TransactionStatus.PENDING_FACE_AUTH : TransactionStatus.PENDING_OTP;
        
        Transaction transaction = Transaction.builder()
                .senderAccountId(request.getSenderAccountId())
                .senderAccountNumber(request.getSenderAccountNumber())
                .senderUserId(senderUserId)
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .receiverAccountId(receiverAccountId) 
                .receiverUserId(receiverUserId)       
                .amount(request.getAmount())
                .feeAmount(fee)
                .transactionType(request.getTransactionType())
                .status(initialStatus)
                .description(request.getDescription())
                // Saga Orchestration Fields
                .correlationId(correlationId)
                .currentStep(com.uit.transactionservice.entity.SagaStep.STARTED)
                .build();
        transaction = transactionRepository.save(transaction);
        
        log.info("Transaction created with ID: {} - Status: {}", 
                transaction.getTransactionId(), initialStatus);

        TransactionResponse response = transactionMapper.toResponse(transaction);
        response.setRequireFaceAuth(requireFaceAuth);

        // 7. If FaceID is NOT required, proceed directly to OTP
        if (!requireFaceAuth) {
            initiateOtpProcess(transaction, phoneNumber);
        }

        return response;
    }

    /**
     * Logic to generate, save and send OTP.
     * Reusable for both standard flow and post-FaceID flow.
     */
    private void initiateOtpProcess(Transaction transaction, String phoneNumber) {
        // Generate and save OTP to Redis
        String otpCode = otpService.generateOTP();
        log.info("Generated OTP Code for Tx {}: {}", transaction.getTransactionId(), otpCode); 
        
        otpService.saveOTP(transaction.getTransactionId(), otpCode, phoneNumber);
        
        // Send OTP notification
        sendOTPNotification(transaction.getTransactionId(), phoneNumber, otpCode);
        log.info("OTP process initiated for transaction: {}", transaction.getTransactionId());
    }

    /**
     * Resend OTP for an existing transaction
     */
    @Transactional
    public String resendOtp(UUID transactionId) {
        log.info("Resend OTP requested for transaction: {}", transactionId);
        long RESEND_COOLDOWN_SECONDS = 3;// e.g., 3 seconds cooldown

        // 1. Find transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException(ErrorCode.TRANSACTION_NOT_FOUND));

        // 2. Check transaction status
        if (transaction.getStatus() != TransactionStatus.PENDING_OTP) {
            log.warn("Resend OTP failed: Transaction {} is not pending OTP verification. Status is {}",
                    transactionId, transaction.getStatus());
            throw new AppException(ErrorCode.TRANSACTION_STATUS_CONFLICT);
        }

        // 3. Check cooldown period to prevent spam
        OTPService.OTPData existingOtpData = otpService.getOtpData(transactionId);
        if (existingOtpData != null) {
            long timeSinceLastOtp = (System.currentTimeMillis() - existingOtpData.getCreatedAt()) / 1000;
            if (timeSinceLastOtp < RESEND_COOLDOWN_SECONDS) {
                log.warn("Resend OTP failed: Cooldown period not met for transaction {}. Please wait {} seconds.",
                        transactionId, RESEND_COOLDOWN_SECONDS - timeSinceLastOtp);
                throw new AppException(ErrorCode.OTP_RESEND_COOLDOWN);
            }
        } else {
            // This case should ideally not happen if status is PENDING_OTP, but handle it defensively
            log.warn("Resend OTP failed: No existing OTP found in Redis for transaction {}", transactionId);
            throw new AppException(ErrorCode.OTP_NOT_FOUND);
        }


        // 4. Generate, save, and send new OTP
        String newOtpCode = otpService.generateOTP();
        String phoneNumber = existingOtpData.getPhoneNumber();
        log.info("Generated new OTP for transaction {}: {}", transactionId, newOtpCode);

        otpService.saveOTP(transaction.getTransactionId(), newOtpCode, phoneNumber);
        log.info("New OTP saved to Redis for transaction: {}", transaction.getTransactionId());

        sendOTPNotification(transaction.getTransactionId(), phoneNumber, newOtpCode);
        log.info("Resend OTP notification sent for transaction: {}", transactionId);
        return newOtpCode;
    }

    /**
     * Get transaction history by account number with filtering
     * type: SENT, RECEIVED, or ALL (null)
     */
    public Page<TransactionResponse> getTransactionHistory(String accountNumber, String type, Pageable pageable) {
        log.info("Getting transaction history for account: {} - Type: {}", accountNumber, type);
        
        Page<Transaction> page;
        if ("SENT".equalsIgnoreCase(type)) {
            page = transactionRepository.findBySenderAccountNumber(accountNumber, pageable);
        } else if ("RECEIVED".equalsIgnoreCase(type)) {
            page = transactionRepository.findByReceiverAccountNumber(accountNumber, pageable);
        } else {
            // ALL
            page = transactionRepository.findBySenderAccountNumberOrReceiverAccountNumber(accountNumber, accountNumber, pageable);
        }
        
        return page.map(transactionMapper::toResponse);
    }

    /**
     * Get transaction history by status
     */
    public Page<TransactionResponse> getTransactionHistoryByStatus(TransactionStatus status, Pageable pageable) {
        log.info("Getting transaction history with status: {}", status);
        return transactionRepository.findByStatus(status, pageable)
                .map(transactionMapper::toResponse);
    }

    /**
     * Get transaction by ID
     */
    public TransactionResponse getTransactionById(UUID transactionId) {
        log.info("Getting transaction: {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        return transactionMapper.toResponse(transaction);
    }

    /**
     * Calculate transaction fee - TEMPORARILY DISABLED
     * Fee is set to ZERO for all transactions
     */
    private BigDecimal calculateFee(TransactionType type, BigDecimal amount) {
        // TODO: Re-enable fee calculation when needed
        // TransactionFee feeConfig = transactionFeeRepository.findByTransactionType(type)
        //         .orElseThrow(() -> new RuntimeException("Fee configuration not found for type: " + type));
        // return feeConfig.getFeeAmount();
        
        log.debug("Fee calculation disabled - returning ZERO for type: {}", type);
        return BigDecimal.ZERO;
    }

    /**
     * Check if transaction exceeds limits
     */
    private void checkTransactionLimit(String accountId, BigDecimal amount) {
        TransactionLimit limit = transactionLimitRepository.findById(accountId)
                .orElseGet(() -> createDefaultLimit(accountId));

        // Reset limits if necessary
        resetLimitsIfNeeded(limit);

        // Check daily limit
        BigDecimal newDailyUsed = limit.getDailyUsed().add(amount);
        if (newDailyUsed.compareTo(limit.getDailyLimit()) > 0) {
            throw new RuntimeException("Daily transaction limit exceeded");
        }

        // Check monthly limit
        BigDecimal newMonthlyUsed = limit.getMonthlyUsed().add(amount);
        if (newMonthlyUsed.compareTo(limit.getMonthlyLimit()) > 0) {
            throw new RuntimeException("Monthly transaction limit exceeded");
        }
    }

    /**
     * Update transaction limit after successful transaction
     */
    private void updateTransactionLimit(String accountId, BigDecimal amount) {
        TransactionLimit limit = transactionLimitRepository.findById(accountId)
                .orElseGet(() -> createDefaultLimit(accountId));

        resetLimitsIfNeeded(limit);

        limit.setDailyUsed(limit.getDailyUsed().add(amount));
        limit.setMonthlyUsed(limit.getMonthlyUsed().add(amount));
        transactionLimitRepository.save(limit);

        log.debug("Updated transaction limit for account: {}", accountId);
    }

    /**
     * Create default transaction limit
     */
    private TransactionLimit createDefaultLimit(String accountId) {
        TransactionLimit limit = TransactionLimit.builder()
                .accountId(accountId)
                .dailyLimit(BigDecimal.valueOf(50000)) // Default $50,000
                .monthlyLimit(BigDecimal.valueOf(200000)) // Default $200,000
                .dailyUsed(BigDecimal.ZERO)
                .monthlyUsed(BigDecimal.ZERO)
                .lastDailyReset(LocalDateTime.now())
                .lastMonthlyReset(LocalDateTime.now())
                .build();

        return transactionLimitRepository.save(limit);
    }

    /**
     * Reset limits if period has passed
     */
    private void resetLimitsIfNeeded(TransactionLimit limit) {
        LocalDateTime now = LocalDateTime.now();

        // Reset daily if more than 24 hours
        if (limit.getLastDailyReset().plusDays(1).isBefore(now)) {
            limit.setDailyUsed(BigDecimal.ZERO);
            limit.setLastDailyReset(now);
        }

        // Reset monthly if more than 30 days
        if (limit.getLastMonthlyReset().plusDays(30).isBefore(now)) {
            limit.setMonthlyUsed(BigDecimal.ZERO);
            limit.setLastMonthlyReset(now);
        }
    }

    /**
     * Send OTP notification via event
     */
    private void sendOTPNotification(UUID transactionId, String phoneNumber, String otpCode) {
        try {
            // Map<String, Object> payload = new HashMap<>();
            // payload.put("eventType", "OTPGenerated");
            // payload.put("transactionId", transactionId.toString());
            // payload.put("phoneNumber", phoneNumber);
            // payload.put("otpCode", otpCode);
            // payload.put("message", "Your OTP for transaction is: " + otpCode + ". Valid for 5 minutes.");
            // payload.put("timestamp", LocalDateTime.now().toString());

            // String payloadJson = objectMapper.writeValueAsString(payload);

            // OutboxEvent event = OutboxEvent.builder()
            //         .aggregateId(transactionId.toString())
            //         .aggregateType("Transaction")
            //         .eventType("OTPGenerated")
            //         .exchange(RabbitMQConstants.TRANSACTION_EXCHANGE)
            //         .routingKey("otp.generated")
            //         .payload(payloadJson)
            //         .status(OutboxEventStatus.PENDING)
            //         .build();

            // outboxEventRepository.save(event);
           OtpEventDto  otpEvent = OtpEventDto.builder()
                    .transactionId(transactionId.toString())
                    .phoneNumber(phoneNumber)
                    .otpCode(otpCode)
                    .build();
                
            // Publish OTP event directly
            notificationEventPublisher.publishOtp(otpEvent);

            log.info("OTP notification event created for transaction: {}", transactionId);

        } catch (Exception e) {
            log.error("Failed to create OTP notification event", e);
            // Don't throw exception, continue with transaction
        }
    }

    /**
     * Verify OTP and route to appropriate transfer handler
     */
    @Transactional
    public TransactionResponse verifyOTP(UUID transactionId, String otpCode) {
        log.info("Verifying OTP for transaction: {}", transactionId);

        // 1. Find transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

       
        // 2. Check transaction status
        if (transaction.getStatus() != TransactionStatus.PENDING_OTP) {
            throw new RuntimeException("Transaction is not pending OTP verification");
        }

        // 3. Verify OTP using OTPService
        OTPService.OTPVerificationResult result = otpService.verifyOTP(transactionId, otpCode);

        // 4. Handle verification result
        if (!result.isSuccess()) {
            if (result.getMessage().contains("expired")) {
                transaction.setStatus(TransactionStatus.OTP_EXPIRED);
                transaction.setFailureReason("OTP has expired. Please request a new OTP.");
                transaction = transactionRepository.save(transaction);
                log.warn("OTP expired for transaction: {}", transactionId);
            } else if (result.getMessage().contains("Maximum")) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason("Maximum OTP attempts exceeded.");
                transaction = transactionRepository.save(transaction);
                log.warn("Maximum OTP attempts reached for transaction: {}", transactionId);
            } else {

        log.warn("User entered invalid OTP for transaction: {}. {}", transactionId, result.getMessage());
        throw new AppException(ErrorCode.INVALID_OTP);
    }
            
            // Return response with failure reason instead of throwing exception
            return transactionMapper.toResponse(transaction);
        }

        // 5. Update transaction status to PENDING (OTP verified, processing payment)
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setCurrentStep(SagaStep.OTP_VERIFIED);
        transaction = transactionRepository.save(transaction);
        log.info("OTP verified successfully - Transaction: {} moved to PENDING status", transactionId);

        // 6. Route to appropriate handler based on transfer type
        log.info("transaction type:" + transaction.getTransactionType() );
        if (transaction.isInternalTransfer()) {
            return processInternalTransfer(transaction);
        } else {
            return processExternalTransfer(transaction);
        }
    }

    /**
     * Process INTERNAL transfer (within FortressBank) - SYNCHRONOUS
     * Uses atomic endpoint for guaranteed consistency
     */
    private TransactionResponse processInternalTransfer(Transaction transaction) {
        log.info("Processing INTERNAL transfer - TxID: {}", transaction.getTransactionId());

        // No fee for now - totalAmount = amount only
        BigDecimal totalAmount = transaction.getAmount();
        
        try {
            // Use atomic internal transfer endpoint (recommended approach)
            log.info("Executing atomic internal transfer - From: {} To: {} Amount: {}", 
                    transaction.getSenderAccountId(), 
                    transaction.getReceiverAccountId(), 
                    totalAmount);
            
            InternalTransferResponse transferResponse = accountServiceClient.executeInternalTransfer(
                    transaction.getSenderAccountId(),
                    transaction.getReceiverAccountId(),
                    totalAmount,
                    transaction.getTransactionId().toString(),
                    transaction.getDescription()
            );

            // Update transaction with success
            transaction.setCurrentStep(SagaStep.COMPLETED);
            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            transaction = transactionRepository.save(transaction);

            // Update transaction limit
            updateTransactionLimit(transaction.getSenderAccountId(), totalAmount);
            
            // Send success notification ASYNCHRONOUSLY (non-critical)
            sendTransactionNotification(transaction, "TransactionCompleted", true,0);
            
            log.info("Internal transfer completed - TxID: {} - Sender new balance: {} - Receiver new balance: {}",
                    transaction.getTransactionId(), 
                    transferResponse.getSenderAccountNewBalance(),
                    transferResponse.getReceiverAccountNewBalance());
            
            // Centralized Audit Log
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("transaction-service")
                    .entityType("Transaction")
                    .entityId(transaction.getTransactionId().toString())
                    .action("INTERNAL_TRANSFER")
                    .userId(transaction.getSenderUserId())
                    .newValues(Map.of(
                        "senderAccountId", transaction.getSenderAccountId(),
                        "receiverAccountId", transaction.getReceiverAccountId(),
                        "amount", totalAmount,
                        "status", TransactionStatus.COMPLETED.toString()
                    ))
                    .changes("Internal transfer completed")
                    .result("SUCCESS")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);


            return transactionMapper.toResponse(transaction);

        } catch (InsufficientBalanceException e) {
            // Business logic error: insufficient balance
            log.error("Internal transfer {} failed: Insufficient balance", transaction.getTransactionId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCurrentStep(SagaStep.FAILED);
            transaction.setFailureReason("Insufficient balance");
            transactionRepository.save(transaction);
            
            sendTransactionNotification(transaction, "TransactionFailed", false,0);
            throw new RuntimeException("Insufficient balance in sender account", e);

        } catch (AccountServiceException e) {
            // Technical error: account service unavailable or error
            log.error("Internal transfer {} failed: Account service error", transaction.getTransactionId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCurrentStep(SagaStep.FAILED);
            transaction.setFailureReason("Account service error: " + e.getMessage());
            transactionRepository.save(transaction);
            
            sendTransactionNotification(transaction, "TransactionFailed", false,0);
            throw new RuntimeException("Failed to process internal transfer: " + e.getMessage(), e);

        } catch (Exception e) {
            // Unexpected error
            log.error("Unexpected error during internal transfer: {}", transaction.getTransactionId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCurrentStep(SagaStep.FAILED);
            transaction.setFailureReason("Unexpected error: " + e.getMessage());
            transactionRepository.save(transaction);
            
            sendTransactionNotification(transaction, "TransactionFailed", false,0);
            throw new RuntimeException("Unexpected error during transfer", e);
        }
    }


    private TransactionResponse processExternalTransfer(Transaction transaction) {
        log.info("Processing EXTERNAL transfer - TxID: {} - To Bank: {}", 
                transaction.getTransactionId(), transaction.getDestinationBankCode());

        // No fee for now - totalAmount = amount only
        BigDecimal totalAmount = transaction.getAmount();
        
        try {

            log.info("Step 1: Debiting sender account {} - Amount: {}", 
                    transaction.getSenderAccountId(), totalAmount);
            
            AccountBalanceResponse debitResponse = accountServiceClient.debitAccount(
                    transaction.getSenderAccountId(),
                    totalAmount,
                    transaction.getTransactionId().toString(),
                    "External transfer to " + transaction.getDestinationBankCode()
            );
            
            transaction.setCurrentStep(SagaStep.DEBIT_COMPLETED);
            transaction = transactionRepository.save(transaction);



            log.info("Debit completed - New sender balance: {}", debitResponse.getNewBalance());

            // Step 2: Call Stripe Transfer API (sync with Resilience4j retry)
            String idempotencyKey = UUID.randomUUID().toString();
            transaction.setIdempotencyKey(idempotencyKey);
            log.info("Step 2: Calling Stripe Transfer API - IdempotencyKey: {}", idempotencyKey);
            
            try {
                // Create Stripe transfer request (to Connected Account)
                StripeTransferRequest stripeRequest = StripeTransferRequest.builder()
                        .amount(totalAmount.multiply(BigDecimal.valueOf(100)).longValue()) // Convert to cents
                        .currency("usd")
                        .destination(transaction.getReceiverAccountNumber()) // Connected Account ID (e.g., "acct_xxxxx")
                        // .destination("acct_1ScVfURovHoUHamy") // Connected Account ID (e.g., "acct_xxxxx")
                        .description(transaction.getDescription())
                        .metadata(Map.of(
                                "transaction_id", transaction.getTransactionId().toString(),
                                "sender_account_id", transaction.getSenderAccountId(),
                                "correlation_id", transaction.getCorrelationId()
                        ))
                        .transferGroup(transaction.getCorrelationId()) // Group related transfers
                        .build();
                
                // Call Stripe Transfer API (Resilience4j will retry 3 times if fails)
                StripeTransferResponse stripeResponse = stripeTransferService.createTransfer(stripeRequest);
                
                // Update transaction with Stripe transfer ID
                transaction.setStripeTransferId(stripeResponse.getId()); // Store transfer ID
                transaction.setStripeTransferStatus("completed"); // Transfer completed
                transaction.setCurrentStep(SagaStep.EXTERNAL_INITIATED);
                transaction.setStatus(TransactionStatus.PENDING);
                transaction = transactionRepository.save(transaction);
                
                log.info("Stripe transfer created successfully - TransferID: {} - Destination: {} - TxID: {}",
                        stripeResponse.getId(), stripeResponse.getDestination(), transaction.getTransactionId());
                
            } catch (StripeException e) {
                // Stripe API failed after retries - Rollback debit
                log.error("Stripe transfer failed - TxID: {} - Error: {}", transaction.getTransactionId(), e.getMessage());
                
                try {
                    log.warn("Rolling back debit after Stripe failure - TxID: {}", transaction.getTransactionId());
                    accountServiceClient.rollbackDebit(
                            transaction.getSenderAccountId(),
                            totalAmount,
                            transaction.getTransactionId().toString()
                    );
                    transaction.setCurrentStep(SagaStep.ROLLBACK_COMPLETED);
                    transaction.setStatus(TransactionStatus.FAILED);
                    log.info("Rollback completed - TxID: {}", transaction.getTransactionId());
                } catch (Exception rollbackError) {
                    log.error("CRITICAL: Rollback failed - TxID: {} - Manual intervention required!",
                            transaction.getTransactionId(), rollbackError);
                    transaction.setCurrentStep(SagaStep.ROLLBACK_FAILED);
                    transaction.setStatus(TransactionStatus.ROLLBACK_FAILED);
                }
                
                transaction.setFailureReason("Stripe API error: " + e.getMessage());
                transaction.setStripeFailureCode(e.getCode());
                transaction.setStripeFailureMessage(e.getMessage());
                transactionRepository.save(transaction);
                sendTransactionNotification(transaction, "TransactionFailed", false,1);
                
                throw new RuntimeException("Stripe transfer failed: " + e.getMessage(), e);
            }

            // Update transaction limit
            updateTransactionLimit(transaction.getSenderAccountId(), totalAmount);

            // Send notification that transfer is being processed
            sendTransactionNotification(transaction, "ExternalTransferInitiated", false,1);
            
            // Centralized Audit Log
            AuditEventDto auditEvent = AuditEventDto.builder()
                    .serviceName("transaction-service")
                    .entityType("Transaction")
                    .entityId(transaction.getTransactionId().toString())
                    .action("EXTERNAL_TRANSFER_INIT")
                    .userId(transaction.getSenderUserId())
                    .newValues(Map.of(
                        "senderAccountId", transaction.getSenderAccountId(),
                        "receiverAccountNumber", transaction.getReceiverAccountNumber(),
                        "amount", totalAmount,
                        "destinationBank", transaction.getDestinationBankCode() != null ? transaction.getDestinationBankCode() : "Stripe",
                        "stripeTransferId", transaction.getStripeTransferId(),
                        "status", TransactionStatus.PENDING.toString()
                    ))
                    .changes("External transfer initiated via Stripe")
                    .result("PENDING")
                    .build();
            auditEventPublisher.publishAuditEvent(auditEvent);

            return transactionMapper.toResponse(transaction);

        } catch (InsufficientBalanceException e) {
            log.error("External transfer {} failed: Insufficient balance", transaction.getTransactionId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCurrentStep(SagaStep.FAILED);
            transaction.setFailureReason("Insufficient balance");
            transactionRepository.save(transaction);
            
            sendTransactionNotification(transaction, "TransactionFailed", false,1);
            throw new RuntimeException("Insufficient balance in sender account", e);

        } catch (Exception e) {
            // Unexpected error during processing
            log.error("External transfer {} failed during processing", transaction.getTransactionId(), e);
            
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setCurrentStep(SagaStep.FAILED);
            transaction.setFailureReason("Unexpected error: " + e.getMessage());
            transactionRepository.save(transaction);
            sendTransactionNotification(transaction, "TransactionFailed", false,1);
            
            throw new RuntimeException("External transfer failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send transaction notification asynchronously (non-blocking)
     */
    private void sendTransactionNotification(Transaction transaction, String eventType, boolean success, Integer notiWho) {
        try {
            // Use user IDs directly from transaction entity
            String safeSenderUserId = transaction.getSenderUserId() != null ? transaction.getSenderUserId() : "";
            String safeReceiverUserId = transaction.getReceiverUserId() != null ? transaction.getReceiverUserId() : "";

            // Log for debugging (optional)
            log.debug("Notification - SenderUser: {}, ReceiverUser: {}", safeSenderUserId, safeReceiverUserId);

            // Map<String, Object> payload = new HashMap<>();
            // payload.put("senderUserId", safeSenderUserId);
            // payload.put("receiverUserId", safeReceiverUserId);
            // payload.put("transactionId", transaction.getTransactionId().toString());
            // payload.put("senderAccountId", transaction.getSenderAccountId());
            // payload.put("receiverAccountId", transaction.getReceiverAccountId());
            // payload.put("amount", transaction.getAmount());
            // payload.put("status", transaction.getStatus().toString());
            // payload.put("success", success);
            // payload.put("message", success ? "Transaction completed successfully" : "Transaction failed: " + transaction.getFailureReason());
            // payload.put("timestamp", LocalDateTime.now().toString());

            // String payloadJson = objectMapper.writeValueAsString(payload);

            // OutboxEvent event = OutboxEvent.builder()
            //         .aggregateId(transaction.getTransactionId().toString())
            //         .aggregateType("Transaction")
            //         .eventType(eventType)
            //         .exchange(RabbitMQConstants.TRANSACTION_EXCHANGE)
            //         .routingKey("notification." + eventType)
            //         .payload(payloadJson)
            //         .status(OutboxEventStatus.PENDING)
            //         .build();

            // outboxEventRepository.save(event);
            // mapper payload to NotificationEventDto
            if (eventType=="ExternalTransferInitiated") {
                safeReceiverUserId=null;
            }
            NotificationEventDto  notificationEvent = NotificationEventDto.builder()
                    .transactionId(transaction.getTransactionId().toString())
                    .senderUserId(safeSenderUserId)
                    .receiverUserId(safeReceiverUserId)
                    .senderAccountNumber(transaction.getSenderAccountNumber())
                    .receiverAccountNumber(transaction.getReceiverAccountNumber())
                    .amount(transaction.getAmount())
                    .status(transaction.getStatus().toString())
                    .success(success)
                    .message(success ? "Transaction completed successfully" : "Transaction failed: " + transaction.getFailureReason())
                    .notiWho(notiWho)
                    .build();
          
            notificationEventPublisher.publishMail(notificationEvent);

            log.info("Notification event {} created for transaction: {}", eventType, transaction.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to create notification event - non-critical", e);
        }
    }

    /**
     * Handle Stripe transfer completed webhook
     */
    @Transactional
    public void handleStripeTransferCompleted(String transactionId, String transferId, String webhookIdempotencyKey) {
        log.info(" Processing Stripe transfer completed - TxID: {} - TransferID: {} - WebhookKey: {}",
                transactionId, transferId, webhookIdempotencyKey);

        // 
        Transaction transaction = transactionRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> {
                    log.error(" Transaction not found: {}", transactionId);
                    return new RuntimeException("Transaction not found: " + transactionId);
                });

        // Idempotency check
        if (transaction.getWebhookReceivedAt() != null && 
            transaction.getStripeTransferStatus() != null &&
            transaction.getStripeTransferStatus().equals("completed")) {
            log.warn("Transaction {} already processed webhook - Duplicate ignored. IdempotencyKey: {}", 
                    transaction.getTransactionId(), webhookIdempotencyKey);
            return;
        }

        // Save to Outbox table FIRST (transactional guarantee)
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("transactionId", transaction.getTransactionId().toString());
            payload.put("transferId", transferId);
            payload.put("webhookIdempotencyKey", webhookIdempotencyKey);
            payload.put("eventType", "TRANSFER_COMPLETED");
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(transaction.getTransactionId().toString())
                    .aggregateType("StripeWebhook")
                    .eventType("TRANSFER_COMPLETED")
                    .exchange("stripe.webhook")
                    .routingKey("stripe.transfer.completed")
                    .payload(payloadJson)
                    .status(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();
            
            outboxEventRepository.save(event);
            log.info("Saved TRANSFER_COMPLETED to outbox - TxID: {} - EventID: {}", transaction.getTransactionId(), event.getEventId());
            
            // Process immediately (best effort)
            processTransferCompletedEvent(transaction, webhookIdempotencyKey);
            
        } catch (Exception e) {
            log.error("Failed to save webhook event to outbox - TxID: {}", transaction.getTransactionId(), e);
            throw new RuntimeException("Failed to handle webhook", e);
        }
    }
    
    /**
     * Process transfer completed event (transfer succeeded)
     */
    @Transactional
    public void processTransferCompletedEvent(Transaction transaction, String webhookIdempotencyKey) {
        // Double-check idempotency
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            log.warn("Transaction {} already COMPLETED - Skipping", transaction.getTransactionId());
            return;
        }

        // Update transaction to COMPLETED
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCurrentStep(SagaStep.COMPLETED);
        transaction.setStripeTransferStatus("completed");
        transaction.setWebhookReceivedAt(LocalDateTime.now());
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        log.info(" Transaction COMPLETED - Transfer succeeded - TxID: {} - TransferID: {}", 
                transaction.getTransactionId(), transaction.getStripeTransferId());
        
        // Push SSE update to client
        TransactionStatusUpdate sseUpdate = TransactionStatusUpdate.success(
                transaction.getTransactionId().toString(),
                transaction.getAmount(),
                transaction.getReceiverAccountId()
        );
        sseService.pushUpdate(transaction.getTransactionId().toString(), sseUpdate);
        
        sendTransactionNotification(transaction, "TransactionCompleted", true,1);

        // Centralized Audit Log
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("transaction-service")
                .entityType("Transaction")
                .entityId(transaction.getTransactionId().toString())
                .action("EXTERNAL_TRANSFER_COMPLETED")
                .userId(transaction.getSenderUserId())
                .newValues(Map.of(
                    "stripeTransferId", transaction.getStripeTransferId(),
                    "status", TransactionStatus.COMPLETED.toString()
                ))
                .changes("External transfer confirmed by Stripe webhook")
                .result("SUCCESS")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);
    }

    /**
     * Handle Stripe transfer failure webhook - ROLLBACK
     * Called when Stripe sends transfer.failed or transfer.reversed event
     */
    @Transactional
    public void handleStripeTransferFailure(String transferId, String failureCode, 
                                         String failureMessage, String webhookIdempotencyKey) {
        log.warn("Received Stripe transfer failure - TransferID: {} - Reason: {} - WebhookKey: {}",
                transferId, failureMessage, webhookIdempotencyKey);

        Transaction transaction = transactionRepository.findByStripeTransferId(transferId)
                .orElseThrow(() -> new RuntimeException("Transaction not found for transfer: " + transferId));

        // Idempotency check
        if (transaction.getWebhookReceivedAt() != null &&
            (transaction.getStatus() == TransactionStatus.FAILED ||
             transaction.getStatus() == TransactionStatus.ROLLBACK_COMPLETED)) {
            log.warn("Transaction {} already processed failure webhook - Duplicate ignored", transaction.getTransactionId());
            return;
        }

        // Save to Outbox FIRST
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("transactionId", transaction.getTransactionId().toString());
            payload.put("transferId", transferId);
            payload.put("failureCode", failureCode);
            payload.put("failureMessage", failureMessage);
            payload.put("webhookIdempotencyKey", webhookIdempotencyKey);
            payload.put("eventType", "TRANSFER_FAILURE");
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(transaction.getTransactionId().toString())
                    .aggregateType("StripeWebhook")
                    .eventType("TRANSFER_FAILURE")
                    .exchange("stripe.webhook")
                    .routingKey("stripe.transfer.failure")
                    .payload(payloadJson)
                    .status(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();
            
            outboxEventRepository.save(event);
            log.info("Saved TRANSFER_FAILURE to outbox - TxID: {} - EventID: {}", transaction.getTransactionId(), event.getEventId());
            
            // Process immediately (best effort)
            processTransferFailureEvent(transaction, transferId, failureCode, failureMessage);
            
        } catch (Exception e) {
            log.error("Failed to save webhook event to outbox - TxID: {}", transaction.getTransactionId(), e);
            throw new RuntimeException("Failed to handle webhook", e);
        }
    }
    
    /**
     * Process transfer failure event with rollback
     */
    @Transactional
    public void processTransferFailureEvent(Transaction transaction, String transferId, 
                                         String failureCode, String failureMessage) {
        // Double-check idempotency
        if (transaction.getStatus() == TransactionStatus.FAILED ||
            transaction.getStatus() == TransactionStatus.ROLLBACK_COMPLETED) {
            log.warn("Transaction {} already failed - Skipping", transaction.getTransactionId());
            return;
        }

        transaction.setStripeTransferStatus("failed");
        transaction.setStripeTransferId(transferId);
        transaction.setStripeFailureCode(failureCode);
        transaction.setStripeFailureMessage(failureMessage);
        transaction.setWebhookReceivedAt(LocalDateTime.now());

        // Rollback: Refund sender account
        BigDecimal refundAmount = transaction.getAmount();

        try {
            log.info("Rolling back Stripe transfer failure - Refunding {} to account {}",
                    refundAmount, transaction.getSenderAccountId());

            accountServiceClient.creditAccount(
                    transaction.getSenderAccountId(),
                    refundAmount,
                    transaction.getTransactionId().toString(),
                    "Refund for failed Stripe transfer: " + failureMessage
            );

            transaction.setCurrentStep(SagaStep.ROLLBACK_COMPLETED);
            transaction.setStatus(TransactionStatus.FAILED);
            log.info("Rollback completed for Stripe transfer failure - TxID: {}", transaction.getTransactionId());

        } catch (Exception e) {
            log.error("CRITICAL: Rollback failed for Stripe transfer failure - TxID: {} - Amount: {} - Manual intervention required!",
                    transaction.getTransactionId(), refundAmount, e);

            transaction.setCurrentStep(SagaStep.ROLLBACK_FAILED);
            transaction.setStatus(TransactionStatus.ROLLBACK_FAILED);
        }

        transaction.setFailureReason("Stripe transfer failed: " + failureMessage);
        transactionRepository.save(transaction);
        
        // Push SSE update to client
        TransactionStatusUpdate sseUpdate = TransactionStatusUpdate.failed(
                transaction.getTransactionId().toString(),
                failureCode,
                failureMessage
        );
        sseService.pushUpdate(transaction.getTransactionId().toString(), sseUpdate);
        
        sendTransactionNotification(transaction, "TransactionFailed", false,1);

        // Centralized Audit Log
        AuditEventDto auditEvent = AuditEventDto.builder()
                .serviceName("transaction-service")
                .entityType("Transaction")
                .entityId(transaction.getTransactionId().toString())
                .action("EXTERNAL_TRANSFER_FAILED")
                .userId(transaction.getSenderUserId())
                .newValues(Map.of(
                    "stripeTransferId", transaction.getStripeTransferId() != null ? transaction.getStripeTransferId() : "",
                    "failureReason", failureMessage,
                    "status", TransactionStatus.FAILED.toString(),
                    "rollbackStatus", transaction.getStatus().toString()
                ))
                .errorMessage(failureMessage)
                .changes("External transfer failed and rolled back")
                .result("FAILURE")
                .build();
        auditEventPublisher.publishAuditEvent(auditEvent);
    }
     
    @Transactional
    public void confirmFaceAuth(ConfirmFaceAuthRequest request) {
        UUID transactionId = request.getTransactionId();
        log.info("Confirming FaceID authentication for transaction: {}", transactionId);
        
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        if (transaction.getStatus() != TransactionStatus.PENDING_FACE_AUTH) {
            log.error("Invalid transaction status for FaceID confirmation: {}", transaction.getStatus());
            throw new AppException(ErrorCode.TRANSACTION_STATUS_CONFLICT, "Transaction is not pending FaceID authentication");
        }
        
        // Update status to PENDING_OTP
        transaction.setStatus(TransactionStatus.PENDING_OTP);
        transactionRepository.save(transaction);
        
        // Use phone number from request (provided by User Service)
        String phoneNumber = request.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isEmpty()) {
             // Fallback for safety
             phoneNumber = "+84857311444";
             log.warn("No phone number provided in FaceAuth confirmation, using default: {}", phoneNumber);
        }
        
        initiateOtpProcess(transaction, phoneNumber);
        
        log.info("FaceID confirmed. Transaction {} moved to PENDING_OTP.", transactionId);
    }
}