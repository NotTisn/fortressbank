package com.uit.transactionservice.client.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RiskAssessmentResponse {
    private String riskLevel;
    private String challengeType;
    private Integer riskScore;
}
