package com.uit.transactionservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RiskAssessmentRequest {
    private BigDecimal amount;
    private String userId;
    private String payeeId;
    private String deviceFingerprint;
    private String ipAddress;
    private String location;
}
