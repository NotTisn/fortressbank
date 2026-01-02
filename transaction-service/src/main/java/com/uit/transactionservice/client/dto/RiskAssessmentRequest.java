package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for risk-engine risk assessment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessmentRequest {
    private String userId;
    private BigDecimal amount;
    private String deviceFingerprint;
    private String ipAddress;
    private String location;
}
