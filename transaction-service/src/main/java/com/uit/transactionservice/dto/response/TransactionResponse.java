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
    private boolean requireFaceAuth;
    
    // Biometric verification fields (Smart OTP / Vietnamese e-banking style)
    private String challengeType;  // NONE, SMS_OTP, DEVICE_BIO, FACE_VERIFY
    private String challengeData;  // Random challenge for device signing
}
