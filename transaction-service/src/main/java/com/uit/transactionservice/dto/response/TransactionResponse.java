package com.uit.transactionservice.dto.response;

import com.uit.transactionservice.entity.TransactionStatus;
import com.uit.transactionservice.entity.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private UUID transactionId;
    private String senderAccountId;
    private String senderAccountNumber;
    private String senderUserId;
    private String receiverAccountId;
    private String receiverAccountNumber;
    private String receiverUserId;
    private BigDecimal amount;
    private BigDecimal feeAmount;
    private TransactionType transactionType;
    private TransactionStatus status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String failureReason;

    // Risk assessment info
    private String riskLevel;
    private Integer riskScore;
    
    /**
     * Challenge type required for this transaction:
     * - NONE: No verification needed (low risk)
     * - SMS_OTP: SMS code verification (medium risk, no device registered)
     * - DEVICE_BIO: Device biometric signature (medium risk, device registered)
     * - FACE_VERIFY: Face re-verification (high risk)
     */
    private String challengeType;
    
    /**
     * Challenge ID for biometric verification (DEVICE_BIO or FACE_VERIFY).
     * Client uses this to submit verification response.
     */
    private String challengeId;
    
    /**
     * Challenge data to be signed by device (for DEVICE_BIO).
     * Base64-encoded random bytes.
     */
    private String challengeData;
}
