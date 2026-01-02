package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from risk-engine risk assessment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskAssessmentResponse {
    /**
     * Risk level: LOW, MEDIUM, HIGH
     */
    private String riskLevel;
    
    /**
     * Risk score from 0-100
     */
    private Integer riskScore;
    
    /**
     * Required challenge type: NONE, SMS_OTP, SMART_OTP
     */
    private String challengeType;
}
