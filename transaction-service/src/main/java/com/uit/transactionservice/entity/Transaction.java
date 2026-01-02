package com.uit.transactionservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_sender_id_date", columnList = "sender_account_id,created_at"),
    @Index(name = "idx_sender_num_date", columnList = "sender_account_number,created_at"),
    @Index(name = "idx_receiver_num_date", columnList = "receiver_account_number,created_at"),
    @Index(name = "idx_status_date", columnList = "status,created_at"),
    @Index(name = "idx_correlation_id", columnList = "correlation_id"),
    @Index(name = "idx_current_step", columnList = "current_step")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "transaction_id")
    private java.util.UUID transactionId;

    @Column(name = "sender_account_id", nullable = false)
    private String senderAccountId;

    @Column(name = "sender_account_number", nullable = false)
    private String senderAccountNumber;

    @Column(name = "sender_user_id")
    private String senderUserId;

    @Column(name = "receiver_account_id")
    private String receiverAccountId;

    @Column(name = "receiver_user_id")
    private String receiverUserId;

    @Column(name = "receiver_account_number", nullable = false)
    private String receiverAccountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "fee_amount", precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "transaction_type", length = 20)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    /**
     * Transfer type: INTERNAL (same bank) or EXTERNAL (interbank)
     */
    @Column(name = "transfer_type", length = 20)
    @Enumerated(EnumType.STRING)
    private TransferType transferType;

    /**
     * For external transfers: external bank's transaction reference ID
     */
    @Column(name = "external_transaction_id", length = 255)
    private String externalTransactionId;

    /**
     * For external transfers: destination bank code (e.g., "VCB", "TCB")
     */
    @Column(name = "destination_bank_code", length = 50)
    private String destinationBankCode;

    // ========== Stripe Transfer Fields ==========
    
    /**
     * Stripe transfer ID returned from Stripe API
     */
    @Column(name = "stripe_transfer_id", length = 100)
    private String stripeTransferId;

    /**
     * Current status of Stripe transfer: completed, failed, reversed
     */
    @Column(name = "stripe_transfer_status", length = 50)
    private String stripeTransferStatus;

    /**
     * Stripe failure code if transfer failed
     */
    @Column(name = "stripe_failure_code", length = 100)
    private String stripeFailureCode;

    /**
     * Stripe failure message if transfer failed
     */
    @Column(name = "stripe_failure_message", columnDefinition = "TEXT")
    private String stripeFailureMessage;

    /**
     * Timestamp when webhook was received from Stripe
     */
    @Column(name = "webhook_received_at")
    private LocalDateTime webhookReceivedAt;

    /**
     * Idempotency key for duplicate webhook prevention
     */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    // ========== Risk Assessment Fields ==========
    
    /**
     * Risk level from risk-engine assessment: LOW, MEDIUM, HIGH
     */
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    /**
     * Risk score (0-100) from risk-engine
     */
    @Column(name = "risk_score")
    private Integer riskScore;

    /**
     * Type of challenge required: NONE, DEVICE_BIO, FACE_VERIFY
     * (Mapped from risk-engine: SMS_OTP → DEVICE_BIO, SMART_OTP → FACE_VERIFY)
     */
    @Column(name = "challenge_type", length = 20)
    private String challengeType;

    /**
     * Challenge ID from user-service Smart OTP.
     * Used to correlate challenge-response verification.
     */
    @Column(name = "challenge_id", length = 100)
    private String challengeId;

    // ========== Saga Orchestration Fields ==========
    
    /**
     * Unique identifier for the entire saga workflow
     * Used for idempotency and distributed tracing
     */
    @Column(name = "correlation_id", unique = true, length = 255)
    private String correlationId;

    /**
     * Current step in the saga state machine
     * STARTED -> OTP_VERIFIED -> DEBITED -> CREDITED -> COMPLETED
     * ROLLING_BACK -> ROLLED_BACK (on failure)
     */
    @Column(name = "current_step", length = 50)
    @Enumerated(EnumType.STRING)
    private SagaStep currentStep;

    /**
     * Step where failure occurred (for rollback/debugging)
     */
    @Column(name = "failure_step", length = 50)
    private String failureStep;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // ========== Timestamps ==========
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Check if this is an internal transfer (within FortressBank)
     */
    public boolean isInternalTransfer() {
        return transactionType == TransactionType.INTERNAL_TRANSFER;
    }

    /**
     * Check if this is an external transfer (to another bank)
     */
    public boolean isExternalTransfer() {
        return transactionType == TransactionType.EXTERNAL_TRANSFER;
    }
}
