package com.uit.accountservice.riskengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for recording a completed transfer in velocity tracking.
 * Sent to risk-engine after a successful transfer completes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VelocityRecordRequest {
    
    private String userId;
    private BigDecimal amount;
    private String transactionId; // For correlation/audit purposes
}
