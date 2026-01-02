package com.uit.riskengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for recording a completed transfer in velocity tracking.
 * Called by account-service after a successful transfer completes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VelocityRecordRequest {
    
    private String userId;
    private BigDecimal amount;
    private String transactionId; // For correlation/audit purposes
}
