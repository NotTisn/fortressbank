package com.uit.accountservice.riskengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for velocity record operation.
 * Returns updated daily total for transparency.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VelocityRecordResponse {
    
    private String userId;
    private BigDecimal recordedAmount;
    private BigDecimal newDailyTotal;
    private boolean success;
    private String message;
}
