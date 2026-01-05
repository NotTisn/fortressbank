package com.uit.sharedkernel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventDto implements Serializable {
    private String transactionId;
    private String senderUserId;
    private String senderAccountNumber;
    private String receiverUserId;
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String status;
    private boolean success;
    private String message;
    private String timestamp;
    private Integer notiWho;
}
